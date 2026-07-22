package com.quest_enhance.client;

import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.DecorativeDependencyLines;
import com.quest_enhance.mixin.QuestScreenAccessor;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 为任务和辅助点的批量菜单构建装饰线操作
public final class DecorativeLineMenus {
    private DecorativeLineMenus() {
    }

    // 当前右键对象属于有效多选路径时追加添加、删除和样式操作
    public static List<ContextMenuItem> append(
            List<ContextMenuItem> context_menu,
            QuestScreen quest_screen,
            Movable clicked_object
    ) {
        List<Movable> selected_objects = ((QuestScreenAccessor) (Object) quest_screen)
                .quest_enhance$get_selected_objects();
        if (selected_objects.size() < 2 || !selected_objects.contains(clicked_object)) {
            return context_menu;
        }

        // 只允许任务和辅助点参与折线路径，并保留玩家的选择顺序
        List<String> node_keys = new ArrayList<>(selected_objects.size());
        for (Movable object : selected_objects) {
            if (object instanceof Quest quest) {
                node_keys.add(DecorativeDependencyLines.questNode(quest.getMovableID()));
            } else if (object instanceof ChapterImage image) {
                Optional<String> anchor_key = DecorativeAnchor.nodeKey(image);
                if (anchor_key.isEmpty()) {
                    return context_menu;
                }
                node_keys.add(anchor_key.get());
            } else {
                return context_menu;
            }
        }

        Chapter chapter = clicked_object.getChapter();
        // 添加操作创建装饰折线
        context_menu.add(new ContextMenuItem(
                Component.translatable("quest_enhance.decorative_line.add"),
                Icons.ADD,
                button -> {
                    if (DecorativeDependencyLines.add(chapter, node_keys)) {
                        EditObjectMessage.sendToServer(chapter);
                        quest_screen.refreshQuestPanel();
                    }
                }
        ));

        // 删除操作按节点集合匹配，重新选择顺序不会影响取消
        context_menu.add(new ContextMenuItem(
                Component.translatable("quest_enhance.decorative_line.remove"),
                Icons.REMOVE,
                button -> {
                    if (DecorativeDependencyLines.remove(chapter, node_keys)) {
                        EditObjectMessage.sendToServer(chapter);
                        quest_screen.refreshQuestPanel();
                    }
                }
        ));

        return context_menu;
    }
}
