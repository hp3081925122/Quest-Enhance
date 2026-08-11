package com.quest_enhance.client.quest;

import com.quest_enhance.common.QuestBackground;
import com.quest_enhance.common.QuestViewBackground;
import com.quest_enhance.client.background.BackgroundImagePicker;
import com.quest_enhance.mixin.QuestScreenAccessor;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// 构建普通任务节点背景图片菜单，并限制批量操作的对象范围
public final class QuestBackgroundMenus {
    private QuestBackgroundMenus() {
    }

    // 创建单个普通任务节点的背景图片设置菜单项
    public static ContextMenuItem createSingle(Quest quest, QuestScreen screen) {
        return new ContextMenuItem(
                Component.translatable("quest_enhance.quest_background"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        QuestBackground.get(quest).orElse(null),
                        resource_location -> {
                            QuestBackground.set(quest, resource_location);
                            EditObjectMessage.sendToServer(quest);
                            screen.refreshQuestPanel();
                        }
                )
        );
    }

    // 创建单个普通任务节点的任务详情面板背景图片设置菜单项
    public static ContextMenuItem createViewSingle(Quest quest, QuestScreen screen) {
        return new ContextMenuItem(
                Component.translatable("quest_enhance.quest_view_background"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        QuestViewBackground.get(quest).orElse(null),
                        resource_location -> {
                            QuestViewBackground.set(quest, resource_location);
                            EditObjectMessage.sendToServer(quest);
                            screen.refreshViewQuestPanel();
                        }
                )
        );
    }

    // 在多选任务菜单中加入仅作用于普通任务节点的批量背景图片设置
    public static void appendBulk(List<ContextMenuItem> context_menu, QuestScreen screen) {
        List<Quest> quests = getSelectedQuests(screen);
        if (quests.size() < 2) {
            return;
        }

        context_menu.add(0, new ContextMenuItem(
                Component.translatable("quest_enhance.quest_background_bulk"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        QuestBackground.get(quests.get(0)).orElse(null),
                        resource_location -> {
                            quests.forEach(quest -> {
                                QuestBackground.set(quest, resource_location);
                                EditObjectMessage.sendToServer(quest);
                            });
                            screen.refreshQuestPanel();
                        }
                )
        ));
        context_menu.add(1, new ContextMenuItem(
                Component.translatable("quest_enhance.quest_view_background_bulk"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        QuestViewBackground.get(quests.get(0)).orElse(null),
                        resource_location -> {
                            quests.forEach(quest -> {
                                QuestViewBackground.set(quest, resource_location);
                                EditObjectMessage.sendToServer(quest);
                            });
                            screen.refreshViewQuestPanel();
                        }
                )
        ));
    }

    // 只收集选区中的普通 Quest，图片和链接任务会让批量入口保持不可用
    private static List<Quest> getSelectedQuests(QuestScreen screen) {
        List<Quest> quests = new ArrayList<>();
        for (Movable object : ((QuestScreenAccessor) (Object) screen).quest_enhance$get_selected_objects()) {
            if (!(object instanceof Quest quest)) {
                return List.of();
            }
            quests.add(quest);
        }
        return quests;
    }
}
