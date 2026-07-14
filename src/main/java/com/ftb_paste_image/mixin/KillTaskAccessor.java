package com.ftb_paste_image.mixin;

import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = KillTask.class, remap = false)
public interface KillTaskAccessor {
    // 读取击杀任务当前配置的实体注册名
    @Accessor("entity")
    ResourceLocation ftb_paste_image$get_entity();
}
