package com.quest_enhance.client.description;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public final class QuestDescriptionItemIcon {
    private static final String WIDTH_PROPERTY = "width";
    private static final String HEIGHT_PROPERTY = "height";
    private static final String ALIGN_PROPERTY = "align";
    private static final String FIT_PROPERTY = "fit";
    private static final String TEXT_PROPERTY = "text";
    private static final int MAX_ENCODED_STACK_LENGTH = 16384;

    private QuestDescriptionItemIcon() {
    }

    // 注册 Base64 物品图标标记，避开 FTB 原版图片标记不能嵌套 NBT 花括号的问题
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientTextComponentUtils.addCustomParser(QuestDescriptionItemIcon::parse));
    }

    // 还原 Base64 编码的完整 ItemStack 并套用 FTB 原生图片布局组件
    private static Component parse(String rawText, Map<String, String> properties) {
        String encodedStack = properties.get(QuestDescriptionComponents.ITEM_ICON_PROPERTY);
        if (encodedStack == null) {
            return null;
        }

        Optional<ItemStack> stack = decodeStack(encodedStack);
        if (stack.isEmpty()) {
            QuestEnhance.LOGGER.debug("Failed to parse quest_enhance_item description component: length={}", encodedStack.length());
            return Component.translatable("quest_enhance.description_component.item_icon.invalid")
                    .withStyle(ChatFormatting.RED);
        }

        ImageComponent image = new ImageComponent();
        image.setImage(ItemIcon.getItemIcon(stack.get()));
        image.setWidth(getDimension(properties, WIDTH_PROPERTY, 18));
        image.setHeight(getDimension(properties, HEIGHT_PROPERTY, 18));
        image.setAlign(parseAlign(properties.getOrDefault(ALIGN_PROPERTY, "center")));
        image.setFit("true".equals(properties.get(FIT_PROPERTY)));

        MutableComponent component = MutableComponent.create(image);
        String hoverText = properties.get(TEXT_PROPERTY);
        if (hoverText != null && !hoverText.isBlank()) {
            component.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Component.literal(hoverText.replace("%20", " "))
            )));
        }
        return component;
    }

    // 解码并还原带完整 Data Components 的物品数据，拒绝异常或超长内容
    private static Optional<ItemStack> decodeStack(String encodedStack) {
        if (encodedStack.length() > MAX_ENCODED_STACK_LENGTH) {
            return Optional.empty();
        }
        try {
            String serializedStack = new String(
                    Base64.getUrlDecoder().decode(encodedStack),
                    StandardCharsets.UTF_8
            );
            CompoundTag stackData = TagParser.parseTag(serializedStack);
            ItemStack stack = ItemStack.parseOptional(resolveProvider(), stackData);
            return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
        } catch (IllegalArgumentException | CommandSyntaxException exception) {
            QuestEnhance.LOGGER.debug("Invalid Base64 item icon payload", exception);
            return Optional.empty();
        }
    }

    // 获取当前客户端或任务书可用的注册表访问器
    private static HolderLookup.Provider resolveProvider() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        if (ClientQuestFile.exists()) {
            return ClientQuestFile.INSTANCE.holderLookup();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    // 将对齐字符串映射为 FTB ImageAlign 枚举
    private static ImageComponent.ImageAlign parseAlign(String value) {
        if (value == null) {
            return ImageComponent.ImageAlign.CENTER;
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "left" -> ImageComponent.ImageAlign.LEFT;
            case "right" -> ImageComponent.ImageAlign.RIGHT;
            default -> ImageComponent.ImageAlign.CENTER;
        };
    }

    // 使用 FTB 图片组件可接受的整数尺寸，防止损坏任务描述导致布局异常
    private static int getDimension(Map<String, String> properties, String key, int fallback) {
        try {
            return Math.max(1, Math.min(Integer.parseInt(properties.getOrDefault(key, Integer.toString(fallback))), 1000));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
