package com.ftb_paste_image.mixin;

import dev.ftb.mods.ftbquests.quest.ChapterImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChapterImage.class, remap = false)
public interface ChapterImageAccessor {
    // 更新文字对象复用的点击字符串
    @Accessor("click")
    void ftb_paste_image$set_click(String click);

    // 更新文字对象在章节画布中的宽度
    @Accessor("width")
    void ftb_paste_image$set_width(double width);

    // 更新文字对象在章节画布中的高度
    @Accessor("height")
    void ftb_paste_image$set_height(double height);
}
