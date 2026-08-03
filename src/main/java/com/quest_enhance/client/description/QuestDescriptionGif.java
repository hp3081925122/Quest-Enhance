package com.quest_enhance.client.description;

import com.quest_enhance.client.canvas.ChapterCanvasGif;
import com.quest_enhance.QuestEnhance;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.util.StringUtils;
import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class QuestDescriptionGif {
    private static final String PROPERTY = "quest_enhance_gif";
    private static final String WIDTH_PROPERTY = "width";
    private static final String HEIGHT_PROPERTY = "height";
    private static final String ALIGN_PROPERTY = "align";
    private static final String FIT_PROPERTY = "fit";
    private static final String TEXT_PROPERTY = "text";
    private static final int DEFAULT_WIDTH = 200;

    private QuestDescriptionGif() {
    }

    // 在 FTB Library 解析任务描述时识别本模组的 GIF 标记
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientTextComponentUtils.addCustomParser(QuestDescriptionGif::parse));
    }

    // 使用资源标识保存 GIF，避免任务书引用本地绝对路径
    public static String createMarkup(ResourceLocation resource_location) {
        return "{" + PROPERTY + ":" + resource_location + "}";
    }

    // 保存 GIF 的显示参数，使描述中的动画图片可被再次编辑而不退化为普通图片
    public static String createMarkup(GifData data) {
        StringBuilder markup = new StringBuilder("{")
                .append(PROPERTY).append(':').append(data.resource_location())
                .append(' ').append(WIDTH_PROPERTY).append(':').append(data.width())
                .append(' ').append(HEIGHT_PROPERTY).append(':').append(data.height())
                .append(' ').append(ALIGN_PROPERTY).append(':').append(data.align().name().toLowerCase(Locale.ROOT));
        if (data.fit()) {
            markup.append(' ').append(FIT_PROPERTY).append(":true");
        }
        if (!data.hover_text().isBlank()) {
            markup.append(' ').append(TEXT_PROPERTY).append(':')
                    .append(data.hover_text().replace(" ", "%20"));
        }
        return markup.append('}').toString();
    }

    // 从任务描述原始行恢复 GIF 配置，供图片双击和右键编辑入口复用
    public static Optional<GifData> getData(String raw_text) {
        if (raw_text.length() < 2 || raw_text.charAt(0) != '{' || raw_text.charAt(raw_text.length() - 1) != '}') {
            return Optional.empty();
        }
        return getData(StringUtils.splitProperties(raw_text.substring(1, raw_text.length() - 1)));
    }

    // 将 GIF 标记转换为会在每次绘制时刷新动画帧的图片组件
    private static Component parse(String raw_text, Map<String, String> properties) {
        Optional<GifData> gif_data = getData(properties);
        if (gif_data.isEmpty()) {
            return properties.containsKey(PROPERTY)
                    ? Component.translatable("quest_enhance.gif.description.invalid").withStyle(ChatFormatting.RED)
                    : null;
        }

        GifData data = gif_data.get();
        ImageComponent image = new ImageComponent();
        image.setImage(new DynamicGifIcon(data.resource_location()));
        image.setWidth(data.width());
        image.setHeight(data.height());
        image.setAlign(data.align());
        image.setFit(data.fit());
        MutableComponent component = MutableComponent.create(image);
        if (!data.hover_text().isBlank()) {
            component.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    Component.literal(data.hover_text())
            )));
        }
        return component;
    }

    // 统一校验 GIF 标记并补齐旧标记缺失的布局参数
    private static Optional<GifData> getData(Map<String, String> properties) {
        String raw_location = properties.get(PROPERTY);
        ResourceLocation resource_location = raw_location == null ? null : ResourceLocation.tryParse(raw_location);
        if (!isValidGif(resource_location)) {
            return Optional.empty();
        }

        double aspect_ratio = ChapterCanvasGif.getAspectRatio(resource_location).orElse(1.0D);
        int width = getDimension(properties, WIDTH_PROPERTY, DEFAULT_WIDTH);
        int height = getDimension(
                properties,
                HEIGHT_PROPERTY,
                Math.max(1, (int) Math.round(DEFAULT_WIDTH / aspect_ratio))
        );
        ImageComponent.ImageAlign align;
        try {
            align = ImageComponent.ImageAlign.byName(properties.getOrDefault(ALIGN_PROPERTY, "center"));
        } catch (RuntimeException exception) {
            align = ImageComponent.ImageAlign.CENTER;
        }
        return Optional.of(new GifData(
                resource_location,
                width,
                height,
                align == null ? ImageComponent.ImageAlign.CENTER : align,
                "true".equals(properties.get(FIT_PROPERTY)),
                properties.getOrDefault(TEXT_PROPERTY, "")
        ));
    }

    // 限制描述图片尺寸，避免手动编辑任务文件时生成无效控件尺寸
    private static int getDimension(Map<String, String> properties, String key, int default_value) {
        try {
            return Math.max(1, Math.min(Integer.parseInt(properties.getOrDefault(key, Integer.toString(default_value))), 1000));
        } catch (NumberFormatException exception) {
            return default_value;
        }
    }

    // 仅接受本模组 textures 目录中的 GIF，确保描述标记可随资源包分发
    private static boolean isValidGif(ResourceLocation resource_location) {
        return resource_location != null
                && resource_location.getNamespace().equals(QuestEnhance.MOD_ID)
                && resource_location.getPath().startsWith("textures/")
                && resource_location.getPath().endsWith(".gif");
    }

    // 描述 GIF 的资源和布局数据
    public record GifData(
            ResourceLocation resource_location,
            int width,
            int height,
            ImageComponent.ImageAlign align,
            boolean fit,
            String hover_text
    ) {
    }

    // 委托已有 GIF 解码器，使任务描述和章节画布共用同一组动态纹理
    private static final class DynamicGifIcon extends Icon {
        private final ResourceLocation resource_location;

        private DynamicGifIcon(ResourceLocation resource_location) {
            this.resource_location = resource_location;
        }

        @Override
        public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
            ChapterCanvasGif.getCurrentFrame(this.resource_location)
                    .orElse(Color4I.DARK_GRAY)
                    .draw(graphics, x, y, width, height);
        }

        @Override
        public String toString() {
            return this.resource_location.toString();
        }
    }
}
