package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 读取 FTB 章节列表按钮所属的章节面板
@Mixin(value = ChapterPanel.ListButton.class, remap = false)
public interface ChapterPanelListButtonAccessor {
    // 获取章节列表按钮的父章节面板
    @Accessor("chapterPanel")
    ChapterPanel quest_enhance$get_chapter_panel();
}
