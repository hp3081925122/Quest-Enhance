package com.quest_enhance.client.quest;

import com.quest_enhance.ChapterPrerequisites;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;

// 显示可以作为当前章节前置的任务节点列表
public final class ChapterPrerequisiteSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final List<Quest> available_quests;
    private final Consumer<Quest> selection_callback;

    private ChapterPrerequisiteSelectionScreen(
            Panel parent_panel,
            List<Quest> available_quests,
            Consumer<Quest> selection_callback
    ) {
        this.parent_panel = parent_panel;
        this.available_quests = List.copyOf(available_quests);
        this.selection_callback = selection_callback;
        this.setTitle(Component.translatable("quest_enhance.chapter_prerequisite.select"));
        this.setHasSearchBox(true);
    }

    // 打开任务节点前置选择列表
    public static void open(Panel parent_panel, Chapter target, Consumer<Quest> selection_callback) {
        new ChapterPrerequisiteSelectionScreen(
                parent_panel,
                ChapterPrerequisites.getAvailablePrerequisites(target),
                selection_callback
        ).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        if (this.available_quests.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.chapter_prerequisite.select.empty"),
                    Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        // 显示所属章节、任务标题和十六进制 ID，避免同名任务无法区分
        for (Quest quest : this.available_quests) {
            Component title = Component.empty()
                    .append(Component.literal("["))
                    .append(quest.getChapter().getTitle())
                    .append(Component.literal("] "))
                    .append(quest.getTitle())
                    .append(Component.literal(" (" + quest.getCodeString() + ")"));
            panel.add(SimpleTextButton.create(
                    panel,
                    title,
                    quest.getIcon(),
                    button -> {
                        this.parent_panel.run();
                        this.selection_callback.accept(quest);
                    }
            ));
        }
    }

    @Override
    public boolean onInit() {
        int target_width = Mth.clamp(520, 176, Minecraft.getInstance().getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.available_quests.size() * 20 + 50,
                166,
                Minecraft.getInstance().getWindow().getGuiScaledHeight() * 4 / 5
        );
        this.setSize(target_width, target_height);
        return super.onInit();
    }

    // 返回章节右键菜单所属的任务书界面
    @Override
    public void onBack() {
        this.parent_panel.run();
    }

    // 取消选择时返回任务书界面
    @Override
    protected void doCancel() {
        this.parent_panel.run();
    }

    // 没有底部确认按钮时，确认操作等同于返回任务书界面
    @Override
    protected void doAccept() {
        this.parent_panel.run();
    }
}
