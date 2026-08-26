package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.Chapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Chapter.class, remap = false)
public interface ChapterAccessor {
    // 修改章节始终隐藏状态
    @Accessor("alwaysInvisible")
    void quest_enhance$set_always_invisible(boolean always_invisible);
}
