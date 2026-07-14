package com.ftb_paste_image.client;

import com.ftb_paste_image.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;

import java.util.Optional;

public final class ChapterCanvasText {
    private static final String PREFIX = "ftb_paste_image:text:";

    private ChapterCanvasText() {
    }

    // 判断章节图片是否是本模组保存的画布文字，并读取文字内容
    public static Optional<String> getText(ChapterImage image) {
        String click = image.getClick();
        return click.startsWith(PREFIX)
                ? Optional.of(click.substring(PREFIX.length()))
                : Optional.empty();
    }

    // 把文字写入章节图片已有的字符串字段，继续使用 FTB 原生保存与同步
    public static void setText(ChapterImage image, String text) {
        ((ChapterImageAccessor) (Object) image).ftb_paste_image$set_click(PREFIX + text);
    }

    // 按当前节点尺寸创建与文字原始比例一致的画布对象
    public static ChapterImage create(
            Chapter chapter,
            String text,
            double x,
            double y,
            double quest_button_size,
            Theme theme
    ) {
        ChapterImage image = new ChapterImage(chapter).setPosition(x, y);
        ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
        double safe_button_size = Math.max(1.0, quest_button_size);
        accessor.ftb_paste_image$set_width(Math.max(0.25, (theme.getStringWidth(text) + 2.0) / safe_button_size));
        accessor.ftb_paste_image$set_height(Math.max(0.25, (theme.getFontHeight() + 2.0) / safe_button_size));
        setText(image, text);
        return image;
    }
}
