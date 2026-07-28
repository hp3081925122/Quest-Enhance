package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.ImageClickAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChapterImage.class, remap = false)
public interface ChapterImageAccessor {
    // 读取新版 FTB 的点击动作，以其数据字段保存特殊画布元素信息
    @Accessor("clickAction")
    ImageClickAction quest_enhance$get_click_action();

    // 写入新版 FTB 的点击动作
    @Accessor("clickAction")
    void quest_enhance$set_click_action(ImageClickAction click_action);

    // 更新文字对象在章节画布中的宽度
    @Accessor("width")
    void quest_enhance$set_width(double width);

    // 更新文字对象在章节画布中的高度
    @Accessor("height")
    void quest_enhance$set_height(double height);
}
