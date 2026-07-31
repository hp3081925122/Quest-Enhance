package com.quest_enhance.client.description;

import com.quest_enhance.client.canvas.ChapterCanvasGif;
import com.quest_enhance.QuestEnhance;
import dev.ftb.mods.ftblibrary.client.icon.IconHelper;
import dev.ftb.mods.ftblibrary.client.icon.IconRenderer;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.client.util.ClientTextComponentUtils;
import dev.ftb.mods.ftblibrary.client.util.ImageComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

public final class QuestDescriptionGif {
    private static final String PROPERTY = "quest_enhance_gif";
    private static final int DEFAULT_WIDTH = 200;

    private QuestDescriptionGif() {
    }

    // 在 FTB Library 解析任务描述时识别本模组的 GIF 标记
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientTextComponentUtils.addCustomParser(QuestDescriptionGif::parse));
    }

    // 使用资源标识保存 GIF，避免任务书引用本地绝对路径
    public static String createMarkup(Identifier resource_location) {
        return "{" + PROPERTY + ":" + resource_location + "}";
    }

    // 将 GIF 标记转换为会在每次绘制时刷新动画帧的图片组件
    private static Component parse(String raw_text, Map<String, String> properties) {
        String raw_location = properties.get(PROPERTY);
        Identifier resource_location = raw_location == null ? null : Identifier.tryParse(raw_location);
        if (!isValidGif(resource_location)) {
            return raw_location == null ? null : Component.translatable("quest_enhance.gif.description.invalid")
                    .withStyle(ChatFormatting.RED);
        }

        double aspect_ratio = ChapterCanvasGif.getAspectRatio(resource_location).orElse(1.0D);
        ImageComponent image = new ImageComponent();
        image.setImage(new DynamicGifIcon(resource_location));
        image.setWidth(DEFAULT_WIDTH);
        image.setHeight(Math.max(1, (int) Math.round(DEFAULT_WIDTH / aspect_ratio)));
        return MutableComponent.create(image);
    }

    // 仅接受本模组 textures 目录中的 GIF，确保描述标记可随资源包分发
    private static boolean isValidGif(Identifier resource_location) {
        return resource_location != null
                && resource_location.getNamespace().equals(QuestEnhance.MOD_ID)
                && resource_location.getPath().startsWith("textures/")
                && resource_location.getPath().endsWith(".gif");
    }

    // 委托已有 GIF 解码器，使任务描述和章节画布共用同一组动态纹理
    private static final class DynamicGifIcon extends Icon<DynamicGifIcon> {
        private final Identifier resource_location;

        private DynamicGifIcon(Identifier resource_location) {
            this.resource_location = resource_location;
        }

        @Override
        public IconRenderer<DynamicGifIcon> getRenderer() {
            return (icon, graphics, x, y, width, height) -> IconHelper.renderIcon(
                    ChapterCanvasGif.getCurrentFrame(icon.resource_location).orElse(Color4I.DARK_GRAY),
                    graphics,
                    x,
                    y,
                    width,
                    height
            );
        }

        @Override
        public String toString() {
            return this.resource_location.toString();
        }
    }
}
