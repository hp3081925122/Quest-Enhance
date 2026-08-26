package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BaseQuestFile.class, remap = false)
public interface BaseQuestFileAccessor {
    // 让 KubeJS 动态创建对象后刷新 FTB Quests 的 ID 索引
    @Invoker("refreshIDMap")
    void quest_enhance$refresh_id_map();
}
