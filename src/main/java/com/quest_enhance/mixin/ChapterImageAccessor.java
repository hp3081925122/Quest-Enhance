package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.ImageClickAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChapterImage.class, remap = false)
public interface ChapterImageAccessor {
    @Accessor("clickAction")
    ImageClickAction quest_enhance$get_clickAction();

    @Accessor("clickAction")
    void quest_enhance$set_clickAction(ImageClickAction click_action);

    // 更新文字对象在章节画布中的宽度
    @Accessor("width")
    void quest_enhance$set_width(double width);

    // 更新文字对象在章节画布中的高度
    @Accessor("height")
    void quest_enhance$set_height(double height);
}
