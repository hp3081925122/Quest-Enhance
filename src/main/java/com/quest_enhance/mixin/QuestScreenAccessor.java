package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Movable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = QuestScreen.class, remap = false)
public interface QuestScreenAccessor {
    // 读取当前正在编辑的章节
    @Accessor("selectedChapter")
    Chapter quest_enhance$get_selected_chapter();

    // 读取客户端任务文件和当前队伍数据
    @Accessor("file")
    ClientQuestFile quest_enhance$get_file();

    // 读取画布当前选中的可移动对象
    @Accessor("selectedObjects")
    List<Movable> quest_enhance$get_selected_objects();
}
