package com.quest_enhance;

import com.quest_enhance.mixin.ChapterImageAccessor;
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
        ChapterImage image = new ChapterImage(chapter).setPosition(x, y);
        ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
        accessor.quest_enhance$set_width(0.4D);
        accessor.quest_enhance$set_height(0.4D);
        assignNewId(image);
        return image;
    }

    // 判断章节图片是否是装饰线辅助点
    public static boolean isAnchor(ChapterImage image) {
        return image.getClick().startsWith(PREFIX);
    }

    // 读取辅助点对应的装饰线节点键
    public static Optional<String> nodeKey(ChapterImage image) {
        if (!isAnchor(image)) {
            return Optional.empty();
        }
        String id = image.getClick().substring(PREFIX.length());
        try {
            UUID.fromString(id);
            return Optional.of("a:" + id);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    // 复制辅助点时生成新编号，避免两个图片共享同一个连线节点
    public static void assignNewId(ChapterImage image) {
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_click(PREFIX + UUID.randomUUID());
    }
}
