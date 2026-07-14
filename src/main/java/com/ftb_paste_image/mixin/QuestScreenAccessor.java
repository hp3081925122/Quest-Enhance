package com.ftb_paste_image.mixin;

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
    Chapter ftb_paste_image$get_selected_chapter();

    // 读取客户端任务文件和当前队伍数据
    @Accessor("file")
    ClientQuestFile ftb_paste_image$get_file();

    // 读取画布当前选中的可移动对象
    @Accessor("selectedObjects")
    List<Movable> ftb_paste_image$get_selected_objects();
}
