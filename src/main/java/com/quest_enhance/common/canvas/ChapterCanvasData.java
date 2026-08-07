package com.quest_enhance.common.canvas;

import com.quest_enhance.client.canvas.ChapterImageClickData;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class ChapterCanvasData {
    private static final String GIF_PREFIX = "quest_enhance:gif_v1:";
    private static final String VIDEO_PREFIX = "quest_enhance:video_v1:";
    private static final String TEXT_PREFIX = "quest_enhance:text_v2:";
    private static final String TEXT_SCALE_PREFIX = "quest_enhance:text_v3:";
    private static final String ANCHOR_PREFIX = "quest_enhance:decorative_anchor:";

    private ChapterCanvasData() {
    }

    // 读取画布 GIF 资源标识
    public static Optional<ResourceLocation> getGif(ChapterImage image) {
        if (image == null) {
            return Optional.empty();
        }
        String click = ChapterImageClickData.get(image);
        return click.startsWith(GIF_PREFIX)
                ? Optional.ofNullable(ResourceLocation.tryParse(click.substring(GIF_PREFIX.length())))
                : Optional.empty();
    }

    // 读取画布视频相对路径
    public static Optional<String> getVideo(ChapterImage image) {
        if (image == null) {
            return Optional.empty();
        }
        String click = ChapterImageClickData.get(image);
        return click.startsWith(VIDEO_PREFIX)
                ? normalizeVideoPath(click.substring(VIDEO_PREFIX.length()))
                : Optional.empty();
    }

    // 读取画布文字及字体
    public static Optional<TextData> getText(ChapterImage image, ResourceLocation defaultFont) {
        if (image == null) {
            return Optional.empty();
        }
        String click = ChapterImageClickData.get(image);
        if (click.startsWith(TEXT_SCALE_PREFIX)) {
            String value = click.substring(TEXT_SCALE_PREFIX.length());
            int scale_separator = value.indexOf(':');
            if (scale_separator <= 0) {
                return Optional.empty();
            }

            int font_separator = value.indexOf(':', scale_separator + 1);
            if (font_separator <= scale_separator + 1) {
                return Optional.empty();
            }

            try {
                double scale = Double.parseDouble(value.substring(0, scale_separator));
                int font_length = Integer.parseInt(value.substring(scale_separator + 1, font_separator));
                int font_start = font_separator + 1;
                int font_end = font_start + font_length;
                if (!Double.isFinite(scale) || font_length <= 0 || font_end > value.length()) {
                    return Optional.empty();
                }

                ResourceLocation font = ResourceLocation.tryParse(value.substring(font_start, font_end));
                return font == null
                        ? Optional.empty()
                        : Optional.of(new TextData(value.substring(font_end), font, scale));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        if (click.startsWith(TEXT_PREFIX)) {
            String value = click.substring(TEXT_PREFIX.length());
            int separator = value.indexOf(':');
            if (separator <= 0) {
                return Optional.empty();
            }
            try {
                int fontLength = Integer.parseInt(value.substring(0, separator));
                int fontStart = separator + 1;
                int fontEnd = fontStart + fontLength;
                if (fontLength <= 0 || fontEnd > value.length()) {
                    return Optional.empty();
                }
                ResourceLocation font = ResourceLocation.tryParse(value.substring(fontStart, fontEnd));
                return font == null
                        ? Optional.empty()
                        : Optional.of(new TextData(value.substring(fontEnd), font, 1.0D));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        return click.startsWith("quest_enhance:text:") && defaultFont != null
                ? Optional.of(new TextData(click.substring("quest_enhance:text:".length()), defaultFont, 1.0D))
                : Optional.empty();
    }

    // 编码 GIF 资源标识到原生点击字段
    public static String gifClick(ResourceLocation resource) {
        return GIF_PREFIX + resource;
    }

    // 编码视频相对路径到原生点击字段
    public static Optional<String> videoClick(String path) {
        return normalizeVideoPath(path).map(VIDEO_PREFIX::concat);
    }

    // 编码文字及字体到原生点击字段
    public static String textClick(String text, ResourceLocation font) {
        return textClick(text, font, 1.0D);
    }

    // 编码文字、字体和缩放倍率到原生点击字段。
    public static String textClick(String text, ResourceLocation font, double scale) {
        String fontText = font.toString();
        double safe_scale = Double.isFinite(scale) ? Math.max(0.05D, Math.min(10.0D, scale)) : 1.0D;
        return TEXT_SCALE_PREFIX + Double.toString(safe_scale) + ":" + fontText.length() + ":" + fontText + text;
    }

    // 编码装饰辅助点标识到原生点击字段
    public static String anchorClick(UUID id) {
        return ANCHOR_PREFIX + id;
    }

    // 校验并规范化视频相对路径
    public static Optional<String> normalizeVideoPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains(":")) {
            return Optional.empty();
        }

        Path normalizedPath = Path.of(normalized).normalize();
        return normalizedPath.isAbsolute() || normalizedPath.startsWith("..")
                ? Optional.empty()
                : Optional.of(normalizedPath.toString().replace('\\', '/'));
    }

    public record TextData(String text, ResourceLocation font, double scale) {
    }
}
