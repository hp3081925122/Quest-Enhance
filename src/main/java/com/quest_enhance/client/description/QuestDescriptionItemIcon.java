package com.quest_enhance.client.description;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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

    // 注册 Base64 物品图标标记，避开 FTB 原版图片标记不能嵌套 NBT 花括号的问题。
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientTextComponentUtils.addCustomParser(QuestDescriptionItemIcon::parse));
    }

    // 还原服务端编码的完整 ItemStack 并套用 FTB 原生图片布局组件。
    private static Component parse(String rawText, Map<String, String> properties) {
        String encodedStack = properties.get(QuestDescriptionComponents.ITEM_ICON_PROPERTY);
        if (encodedStack == null) {
            return null;
        }

        Optional<ItemStack> stack = decodeStack(encodedStack);
        if (stack.isEmpty()) {
            return Component.translatable("quest_enhance.description_component.item_icon.invalid")
                    .withStyle(ChatFormatting.RED);
        }

        ImageComponent image = new ImageComponent();
        image.image = ItemIcon.getItemIcon(stack.get());
        image.width = getDimension(properties, WIDTH_PROPERTY, 18);
        image.height = getDimension(properties, HEIGHT_PROPERTY, 18);
        try {
            image.align = ImageComponent.ImageAlign.fromString(properties.getOrDefault(ALIGN_PROPERTY, "center"));
        } catch (RuntimeException exception) {
            image.align = ImageComponent.ImageAlign.CENTER;
        }
        image.fit = "true".equals(properties.get(FIT_PROPERTY));

        MutableComponent component = MutableComponent.create(image);
        String hoverText = properties.get(TEXT_PROPERTY);
        if (hoverText != null && !hoverText.isBlank()) {
            component.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Component.literal(hoverText)
            )));
        }
        return component;
    }

    // 解码并解析完整物品 NBT，拒绝异常或超长描述数据。
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
            ItemStack stack = ItemStack.of(stackData);
            return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
        } catch (IllegalArgumentException | CommandSyntaxException exception) {
            return Optional.empty();
        }
    }

    // 使用 FTB 图片组件可接受的整数尺寸，防止损坏任务描述导致布局异常。
    private static int getDimension(Map<String, String> properties, String key, int fallback) {
        try {
            return Math.max(1, Math.min(Integer.parseInt(properties.getOrDefault(key, Integer.toString(fallback))), 1000));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
