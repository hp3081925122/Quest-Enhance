package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = KillTask.class, remap = false)
public interface KillTaskAccessor {
    // 读取击杀任务当前配置的实体注册名
    @Accessor("entityTypeId")
    Identifier quest_enhance$get_entity();
}
