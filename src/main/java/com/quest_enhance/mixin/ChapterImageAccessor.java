package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.ChapterImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChapterImage.class, remap = false)
public interface ChapterImageAccessor {
    // 更新文字对象复用的点击字符串
    @Accessor("click")
    void quest_enhance$set_click(String click);

    // 更新文字对象在章节画布中的宽度
    @Accessor("width")
    void quest_enhance$set_width(double width);

    // 更新文字对象在章节画布中的高度
    @Accessor("height")
    void quest_enhance$set_height(double height);
}
