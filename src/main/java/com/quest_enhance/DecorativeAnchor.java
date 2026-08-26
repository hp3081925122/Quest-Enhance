package com.quest_enhance;

import com.quest_enhance.mixin.ChapterImageAccessor;
import com.quest_enhance.client.canvas.ChapterImageClickData;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;

import java.util.Optional;
import java.util.UUID;

// 用原生章节图片承载可选中、移动和删除的装饰线辅助点
public final class DecorativeAnchor {
    private static final String PREFIX = "quest_enhance:decorative_anchor:";

    private DecorativeAnchor() {
    }

    // 在画布坐标处创建带独立 UUID 的辅助点
    public static ChapterImage create(Chapter chapter, double x, double y) {
        ChapterImage image = new ChapterImage(0L, chapter).setPosition(x, y);
        ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
        accessor.quest_enhance$set_width(0.4D);
        accessor.quest_enhance$set_height(0.4D);
        assignNewId(image);
        return image;
    }

    // 判断章节图片是否是装饰线辅助点
    public static boolean isAnchor(ChapterImage image) {
        String click_data = ChapterImageClickData.get(image);
        return click_data.startsWith(PREFIX) || isLegacyAnchor(image, click_data);
    }

    // 读取辅助点对应的装饰线节点键
    public static Optional<String> nodeKey(ChapterImage image) {
        String click_data = ChapterImageClickData.get(image);
        if (click_data.startsWith(PREFIX)) {
            String id = click_data.substring(PREFIX.length());
            try {
                UUID.fromString(id);
                return Optional.of("a:" + id);
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        if (!isLegacyAnchor(image, click_data)) {
            return Optional.empty();
        }
        return Optional.of("a:legacy:" + Long.toUnsignedString(image.getId(), 16));
    }

    // 复制辅助点时生成新编号，避免两个图片共享同一个连线节点
    public static void assignNewId(ChapterImage image) {
        ChapterImageClickData.set(image, PREFIX + UUID.randomUUID());
    }

    // 识别当前 26.1.2 已保存但丢失内部标记的旧辅助点
    private static boolean isLegacyAnchor(ChapterImage image, String click_data) {
        return click_data.isBlank()
                && image.getImage().isEmpty()
                && Math.abs(image.getWidth() - 0.4D) < 0.0001D
                && Math.abs(image.getHeight() - 0.4D) < 0.0001D;
    }
}
