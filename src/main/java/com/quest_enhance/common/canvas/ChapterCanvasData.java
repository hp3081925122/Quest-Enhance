package com.quest_enhance.common.canvas;

import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.ImageClickAction;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

// 统一保存和解析章节画布特殊元素的点击数据
public final class ChapterCanvasData {
    private static final String GIF_PREFIX = "quest_enhance:gif_v1:";
    private static final String VIDEO_PREFIX = "quest_enhance:video_v1:";
    private static final String TEXT_PREFIX = "quest_enhance:text_v2:";
    private static final String TEXT_SCALE_PREFIX = "quest_enhance:text_v3:";
    private static final String ANCHOR_PREFIX = "quest_enhance:decorative_anchor:";

    private ChapterCanvasData() {
    }

    // 读取章节图片点击动作中保存的特殊画布数据
    public static String getClickData(ChapterImage image) {
        return ((ChapterImageAccessor) (Object) image).quest_enhance$get_clickAction().actionData();
    }

    // 使用无操作点击动作保存特殊画布数据
    public static void setClickData(ChapterImage image, String click_data) {
        ImageClickAction click_action = ((ChapterImageAccessor) (Object) image)
                .quest_enhance$get_clickAction()
                .withType(ImageClickAction.ActionType.NONE)
                .withData(click_data);
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_clickAction(click_action);
    }

    // 读取画布 GIF 资源标识
    public static Optional<Identifier> getGif(ChapterImage image) {
        if (image == null) {
            return Optional.empty();
        }
        String click_data = getClickData(image);
        return click_data.startsWith(GIF_PREFIX)
                ? Optional.ofNullable(Identifier.tryParse(click_data.substring(GIF_PREFIX.length())))
                : Optional.empty();
    }

    // 读取画布视频相对路径
    public static Optional<String> getVideo(ChapterImage image) {
        if (image == null) {
            return Optional.empty();
        }
        String click_data = getClickData(image);
        return click_data.startsWith(VIDEO_PREFIX)
                ? normalizeVideoPath(click_data.substring(VIDEO_PREFIX.length()))
                : Optional.empty();
    }

    // 读取画布文字及字体
    public static Optional<TextData> getText(ChapterImage image, Identifier default_font) {
        if (image == null) {
            return Optional.empty();
        }
        String click_data = getClickData(image);
        if (click_data.startsWith(TEXT_SCALE_PREFIX)) {
            String value = click_data.substring(TEXT_SCALE_PREFIX.length());
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
                Identifier font = Identifier.tryParse(value.substring(font_start, font_end));
                return font == null
                        ? Optional.empty()
                        : Optional.of(new TextData(value.substring(font_end), font, scale));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        if (click_data.startsWith(TEXT_PREFIX)) {
            String value = click_data.substring(TEXT_PREFIX.length());
            int separator = value.indexOf(':');
            if (separator <= 0) {
                return Optional.empty();
            }
            try {
                int font_length = Integer.parseInt(value.substring(0, separator));
                int font_start = separator + 1;
                int font_end = font_start + font_length;
                if (font_length <= 0 || font_end > value.length()) {
                    return Optional.empty();
                }
                Identifier font = Identifier.tryParse(value.substring(font_start, font_end));
                return font == null
                        ? Optional.empty()
                        : Optional.of(new TextData(value.substring(font_end), font, 1.0D));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        String legacy_prefix = "quest_enhance:text:";
        return click_data.startsWith(legacy_prefix) && default_font != null
                ? Optional.of(new TextData(click_data.substring(legacy_prefix.length()), default_font, 1.0D))
                : Optional.empty();
    }

    // 编码 GIF 资源标识到原生点击字段
    public static String gifClick(Identifier resource_id) {
        return GIF_PREFIX + resource_id;
    }

    // 编码视频相对路径到原生点击字段
    public static Optional<String> videoClick(String path) {
        return normalizeVideoPath(path).map(VIDEO_PREFIX::concat);
    }

    // 编码文字及字体到原生点击字段
    public static String textClick(String text, Identifier font) {
        return textClick(text, font, 1.0D);
    }

    // 编码文字、字体和缩放倍率到原生点击字段
    public static String textClick(String text, Identifier font, double scale) {
        String font_text = font.toString();
        double safe_scale = Double.isFinite(scale) ? Math.max(0.05D, Math.min(10.0D, scale)) : 1.0D;
        return TEXT_SCALE_PREFIX + Double.toString(safe_scale) + ":"
                + font_text.length() + ":" + font_text + text;
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
        Path normalized_path = Path.of(normalized).normalize();
        return normalized_path.isAbsolute() || normalized_path.startsWith("..")
                ? Optional.empty()
                : Optional.of(normalized_path.toString().replace('\\', '/'));
    }

    public record TextData(String text, Identifier font, double scale) {
    }
}
