package com.quest_enhance.client.quest;

import com.quest_enhance.ChapterPrerequisites;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// 为章节右键菜单提供任务节点前置的添加、删除和清空操作
public final class ChapterPrerequisiteMenus {
    private ChapterPrerequisiteMenus() {
    }

    // 创建任务节点前置管理子菜单，每次打开时重新读取当前章节关系
    public static ContextMenuItem create(QuestScreen quest_screen, Chapter target) {
        List<ContextMenuItem> items = new ArrayList<>();
        items.add(new ContextMenuItem(
                Component.translatable("quest_enhance.chapter_prerequisite.add"),
                Icons.ADD,
                button -> ChapterPrerequisiteSelectionScreen.open(
                        quest_screen,
                        target,
                        prerequisite -> {
                            if (ChapterPrerequisites.add(target, prerequisite)) {
                                save(quest_screen, target);
                            }
                        }
                )
        ));

        List<Quest> prerequisites = ChapterPrerequisites.getPrerequisites(target);
        for (Quest prerequisite : prerequisites) {
            items.add(new ContextMenuItem(
                    Component.translatable(
                            "quest_enhance.chapter_prerequisite.remove",
                            prerequisite.getTitle()
                    ),
                    Icons.REMOVE,
                    button -> {
                        if (ChapterPrerequisites.remove(target, prerequisite)) {
                            save(quest_screen, target);
                        }
                    }
            ));
        }

        // 只有存在前置时才显示清空操作，避免给空菜单增加无效按钮
        if (!prerequisites.isEmpty()) {
            items.add(ContextMenuItem.SEPARATOR);
            items.add(new ContextMenuItem(
                    Component.translatable("quest_enhance.chapter_prerequisite.clear"),
                    Icons.BIN,
                    button -> {
                        if (ChapterPrerequisites.clear(target)) {
                            save(quest_screen, target);
                        }
                    }
            ));
        }

        return ContextMenuItem.subMenu(
                Component.translatable("quest_enhance.chapter_prerequisite"),
                Icons.LOCK,
                items
        );
    }

    // 保存任务节点前置并刷新章节侧边栏，使编辑结果立即可见
    private static void save(QuestScreen quest_screen, Chapter target) {
        new EditObjectMessage(target).sendToServer();
        quest_screen.refreshChapterPanel();
    }
}
