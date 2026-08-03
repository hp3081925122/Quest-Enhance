package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.ChapterImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = ChapterImage.class, remap = false)
public interface ChapterImageAccessor {
    // 更新文字对象复用的点击字符串
    @Accessor("click")
    void quest_enhance$set_click(String click);

    // 读取图片配置的悬停文本，用于仅放开 tooltip 的鼠标命中
    @Accessor("hover")
    List<String> quest_enhance$get_hover();

    // 更新文字对象在章节画布中的宽度
    @Accessor("width")
    void quest_enhance$set_width(double width);

    // 更新文字对象在章节画布中的高度
    @Accessor("height")
    void quest_enhance$set_height(double height);
}
