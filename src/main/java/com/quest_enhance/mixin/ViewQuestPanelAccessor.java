package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ViewQuestPanel.class, remap = false)
public interface ViewQuestPanelAccessor {
    // 设置任务详情面板当前显示的描述页
    @Invoker("setCurrentPage")
    void quest_enhance$set_current_page(int page);
}
