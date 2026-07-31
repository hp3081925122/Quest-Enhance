package com.quest_enhance.client.canvas;

import com.quest_enhance.HiddenDependencyLines;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.mixin.QuestScreenAccessor;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 为任务右键菜单提供单条前置线的隐藏设置
public final class HiddenDependencyLineMenus {
    private HiddenDependencyLineMenus() {
    }

    // 将任意任务节点的全部关联前置线编辑入口添加到右键菜单
    public static List<ContextMenuItem> append(
            List<ContextMenuItem> context_menu,
            QuestScreen quest_screen,
            Quest source
    ) {
        if (!((QuestScreenAccessor) (Object) quest_screen).quest_enhance$get_file().canEdit()
                || quest_screen.isViewingQuest()) {
            return context_menu;
        }

        Chapter chapter = source.getChapter();
        List<ContextMenuItem> line_items = new ArrayList<>();
        Set<String> line_keys = new HashSet<>();
        source.streamDependencies()
                .forEach(dependency_object -> {
                    if (dependency_object instanceof Quest dependency) {
                        addLineItem(
                                line_items,
                                line_keys,
                                quest_screen,
                                chapter,
                                source,
                                dependency,
                                Component.empty().append("← ").append(dependency.getTitle())
                        );
                    } else if (dependency_object instanceof QuestLink link) {
                        link.getQuest().ifPresent(dependency -> addLineItem(
                                line_items,
                                line_keys,
                                quest_screen,
                                chapter,
                                source,
                                dependency,
                                Component.empty().append("← ").append(dependency.getTitle())
                        ));
                    }
                })
                ;
        for (Quest dependent : chapter.getQuests()) {
            if (dependent == source || !dependent.streamDependencies().anyMatch(dependency_object ->
                    dependency_object == source
                            || dependency_object instanceof QuestLink link
                            && link.getQuest().orElse(null) == source)) {
                continue;
            }
            addLineItem(
                    line_items,
                    line_keys,
                    quest_screen,
                    chapter,
                    dependent,
                    source,
                    Component.empty().append("→ ").append(dependent.getTitle())
            );
        }

        int insertion_index = context_menu.indexOf(ContextMenuItem.SEPARATOR);
        if (insertion_index < 0) {
            insertion_index = context_menu.size();
        }
        context_menu.add(insertion_index, new ContextMenuItem(
                Component.translatable("quest_enhance.dependency_line.edit"),
                Icons.SETTINGS,
                button -> DependencyLineSelectionScreen.open(button.getParent(), line_items)
        ));
        return context_menu;
    }

    // 将一条任务到前置任务的关系加入选择列表，并避免链接任务产生重复项
    private static void addLineItem(
            List<ContextMenuItem> line_items,
            Set<String> line_keys,
            QuestScreen quest_screen,
            Chapter chapter,
            Quest source,
            Quest dependency,
            Component title
    ) {
        if (source == dependency || !line_keys.add(source.getMovableID() + ":" + dependency.getMovableID())) {
            return;
        }
        line_items.add(new ContextMenuItem(
                title,
                Icons.SETTINGS,
                button -> open(button.getParent(), quest_screen, chapter, source, dependency)
        ));
    }

    // 打开指定前置线的隐藏与悬停显示配置
    private static void open(
            Panel parent_panel,
            QuestScreen quest_screen,
            Chapter chapter,
            Quest source,
            Quest dependency
    ) {
        HiddenDependencyLines.Line existing = HiddenDependencyLines
                .find(chapter, source.getMovableID(), dependency.getMovableID())
                .orElse(null);
        boolean[] hidden = {existing != null};
        boolean[] reveal_on_hover = {existing != null && existing.reveal_on_hover()};
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            QuestEnhance.LOGGER.debug("Closed hidden dependency line editor: accepted={}", accepted);
            if (accepted) {
                HiddenDependencyLines.set(
                        chapter,
                        source.getMovableID(),
                        dependency.getMovableID(),
                        hidden[0],
                        reveal_on_hover[0]
                );
                new EditObjectMessage(chapter).sendToServer();
                quest_screen.refreshQuestPanel();
            }
            parent_panel.run();
        });
        group.addBool("hidden", hidden[0], value -> hidden[0] = value, false)
                .setNameKey("quest_enhance.dependency_line.hidden");
        group.addBool("reveal_on_hover", reveal_on_hover[0], value -> reveal_on_hover[0] = value, false)
                .setNameKey("quest_enhance.dependency_line.reveal_on_hover");

        new EditConfigScreen(group) {
            @Override
            public Component getTitle() {
                return Component.empty()
                        .append(source.getTitle())
                        .append(" ← ")
                        .append(dependency.getTitle());
            }
        }.openGui();
    }
}
