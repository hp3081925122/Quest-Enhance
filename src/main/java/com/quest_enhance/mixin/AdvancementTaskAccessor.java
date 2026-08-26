package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.task.AdvancementTask;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AdvancementTask.class, remap = false)
public interface AdvancementTaskAccessor {
    // 读取进度任务配置的原版进度 ID
    @Accessor("advancement")
    Identifier quest_enhance$get_advancement();
}
