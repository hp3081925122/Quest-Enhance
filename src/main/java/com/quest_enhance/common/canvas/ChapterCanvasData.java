package com.quest_enhance.common.canvas;

import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class ChapterCanvasData {
    public static final String GIF_PREFIX = "quest_enhance:gif_v1:";
    public static final String VIDEO_PREFIX = "quest_enhance:video_v1:";
    public static final String TEXT_LEGACY_PREFIX = "quest_enhance:text:";
    public static final String TEXT_PREFIX = "quest_enhance:text_v2:";
    public static final String ANCHOR_PREFIX = "quest_enhance:decorative_anchor:";

    private ChapterCanvasData() {
    }

    // 读取画布 GIF 使用的资源标识。
    public static Optional<ResourceLocation> getGif(ChapterImage image) {
        String click = image.getClick();
        return click.startsWith(GIF_PREFIX)
                ? ResourceLocation.read(click.substring(GIF_PREFIX.length())).result()
                : Optional.empty();
    }

    // 把 GIF 资源标识编码到 FTB Quests 原生点击字段。
    public static String gifClick(ResourceLocation resourceLocation) {
        return GIF_PREFIX + resourceLocation;
    }

    // 读取并校验画布视频使用的相对路径。
    public static Optional<String> getVideo(ChapterImage image) {
        String click = image.getClick();
        return click.startsWith(VIDEO_PREFIX)
                ? normalizeVideoPath(click.substring(VIDEO_PREFIX.length()))
                : Optional.empty();
    }

    // 把视频相对路径编码到 FTB Quests 原生点击字段。
    public static Optional<String> videoClick(String videoPath) {
        return normalizeVideoPath(videoPath).map(path -> VIDEO_PREFIX + path);
    }

    // 统一校验配置目录或资源包视频使用的相对路径。
    public static Optional<String> normalizeVideoPath(String videoPath) {
        if (videoPath == null || videoPath.isBlank()) {
            return Optional.empty();
        }

        String normalizedPath = videoPath.trim().replace('\\', '/');
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (normalizedPath.isBlank() || normalizedPath.contains(":")) {
            return Optional.empty();
        }

        Path path = Path.of(normalizedPath).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            return Optional.empty();
        }
        return Optional.of(path.toString().replace('\\', '/'));
    }

    // 读取画布文字及其字体，兼容旧版无字体数据。
    public static Optional<TextData> getText(ChapterImage image, ResourceLocation defaultFont) {
        String click = image.getClick();
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
                        : Optional.of(new TextData(value.substring(fontEnd), font));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }

        return click.startsWith(TEXT_LEGACY_PREFIX)
                ? Optional.of(new TextData(click.substring(TEXT_LEGACY_PREFIX.length()), defaultFont))
                : Optional.empty();
    }

    // 把画布文字与字体编码到 FTB Quests 原生点击字段。
    public static String textClick(String text, ResourceLocation font) {
        String fontId = font.toString();
        return TEXT_PREFIX + fontId.length() + ":" + fontId + text;
    }

    // 读取辅助点 UUID，非法数据不会被当作装饰线路径节点。
    public static Optional<UUID> getAnchorId(ChapterImage image) {
        String click = image.getClick();
        if (!click.startsWith(ANCHOR_PREFIX)) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(click.substring(ANCHOR_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    // 为辅助点生成可持久化的独立标识。
    public static String anchorClick(UUID id) {
        return ANCHOR_PREFIX + id;
    }

    public record TextData(String text, ResourceLocation font) {
    }
}
