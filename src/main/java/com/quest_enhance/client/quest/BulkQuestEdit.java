package com.quest_enhance.client.quest;

import com.quest_enhance.mixin.QuestObjectBaseAccessor;
import com.quest_enhance.mixin.QuestScreenAccessor;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.config.gui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.client.gui.widget.ContextMenuItem;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.platform.network.Play2ServerNetworking;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.CreateObjectMessage;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 为多个普通任务节点提供批量编辑和批量添加任务能力
public final class BulkQuestEdit {
    private BulkQuestEdit() {
    }

    // 仅在选区完全由两个及以上普通任务节点组成时加入批量编辑入口
    public static List<ContextMenuItem> append(List<ContextMenuItem> context_menu, QuestScreen screen) {
        List<Quest> quests = getSelectedQuests(screen);
        if (quests.size() < 2) {
            return context_menu;
        }

        context_menu.add(0, new ContextMenuItem(
                Component.translatable("quest_enhance.bulk_edit"),
                Icons.SETTINGS,
                button -> open(screen, quests)
        ));
        context_menu.add(1, new ContextMenuItem(
                Component.translatable("quest_enhance.bulk_add_task"),
                Icons.ADD,
                button -> openTaskTypeSelection(screen, quests)
        ));
        return context_menu;
    }

    // 打开与任务详情加号相同的任务类型选择界面
    private static void openTaskTypeSelection(QuestScreen screen, List<Quest> quests) {
        List<ContextMenuItem> task_items = new ArrayList<>();
        for (TaskType task_type : TaskTypes.TYPES.values()) {
            task_items.add(new ContextMenuItem(
                    task_type.getDisplayName(),
                    task_type.getIconSupplier(),
                    button -> task_type.getGuiProvider().openCreationGui(
                            button.getParent(),
                            quests.getFirst(),
                            task -> addTaskToAll(screen, quests, task)
                    )
            ));
        }
        TaskTypeSelectionScreen.open(screen.questPanel, task_items);
    }

    // 将配置完成的任务复制到全部选中的任务节点并交给服务端创建
    private static void addTaskToAll(QuestScreen screen, List<Quest> quests, Task source_task) {
        for (Quest quest : quests) {
            Task copied_task = QuestObjectBase.copy(
                    source_task,
                    () -> TaskType.createTask(
                            0L,
                            quest,
                            source_task.getType().getTypeId().toString()
                    )
            );
            if (copied_task != null) {
                Play2ServerNetworking.send(CreateObjectMessage.requestCreation(copied_task));
            }
        }
        screen.refreshQuestPanel();
    }

    // 使用首个任务的原生完整属性页，并在确认后将实际变更同步给其余任务
    private static void open(QuestScreen screen, List<Quest> quests) {
        Quest source = quests.getFirst();
        Json5Object source_before = new Json5Object();
        source.writeData(source_before, source.getQuestFile().holderLookup());
        EditableConfigGroup group = new EditableConfigGroup("ftbquests", accepted -> {
            if (accepted && ((QuestObjectBaseAccessor) (Object) source).quest_enhance$validate_edited_config()) {
                applyChanges(source, quests, source_before);
            } else {
                source.readData(source_before, source.getQuestFile().holderLookup());
                source.clearCachedData();
            }
            screen.refreshQuestPanel();
        }) {
            @Override
            public Component getName() {
                MutableComponent type = Component.literal(" [")
                        .append(Component.translatable("ftbquests." + source.getObjectType().getId()))
                        .append("]")
                        .withStyle(source.getObjectType().getColor());
                return Component.empty()
                        .append(source.getTitle().copy().withStyle(ChatFormatting.UNDERLINE))
                        .append(type);
            }
        };
        source.fillConfigGroup(source.createSubGroup(group));

        new EditConfigScreen(group)
                .setAutoclose(true)
                .openGui();
    }

    // 将首个任务确认后发生变化的 JSON5 字段覆盖到其余普通任务
    private static void applyChanges(Quest source, List<Quest> quests, Json5Object source_before) {
        Json5Object source_after = new Json5Object();
        source.writeData(source_after, source.getQuestFile().holderLookup());
        Set<String> changed_keys = new HashSet<>(source_before.keySet());
        changed_keys.addAll(source_after.keySet());

        source.clearCachedData();
        EditObjectMessage.sendToServer(source);
        for (int index = 1; index < quests.size(); index++) {
            Quest target = quests.get(index);
            Json5Object target_data = new Json5Object();
            target.writeData(target_data, target.getQuestFile().holderLookup());
            for (String key : changed_keys) {
                Json5Element previous_value = source_before.get(key);
                Json5Element changed_value = source_after.get(key);
                if (Objects.equals(previous_value, changed_value)) {
                    continue;
                }
                if (changed_value == null) {
                    target_data.remove(key);
                } else {
                    target_data.add(key, changed_value.deepCopy());
                }
            }
            target.readData(target_data, target.getQuestFile().holderLookup());
            target.clearCachedData();
            EditObjectMessage.sendToServer(target);
        }
    }

    // 选区含有图片、任务链接或其他对象时直接返回空列表，避免误改非任务节点
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
