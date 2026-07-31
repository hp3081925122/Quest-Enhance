package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.quest.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuestButton.class, remap = false)
public interface QuestButtonAccessor {
    // 读取按钮实际绘制的任务，链接任务也会返回其关联任务
    @Accessor("quest")
    Quest quest_enhance$get_quest();
}
