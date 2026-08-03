package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.Chapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Chapter.class, remap = false)
public interface ChapterAccessor {
    // 更新章节可见性时保留现有画布元素对象
    @Accessor("alwaysInvisible")
    void quest_enhance$set_always_invisible(boolean alwaysInvisible);
}
