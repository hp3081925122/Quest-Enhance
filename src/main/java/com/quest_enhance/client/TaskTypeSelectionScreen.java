package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public final class TaskTypeSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final List<ContextMenuItem> task_items;

    private TaskTypeSelectionScreen(Panel parent_panel, List<ContextMenuItem> task_items) {
        this.parent_panel = parent_panel;
        this.task_items = List.copyOf(task_items);
        this.setTitle(Component.translatable("quest_enhance.chapter_task_select"));
        this.setHasSearchBox(true);
    }

    public static void open(Panel parent_panel, List<ContextMenuItem> task_items) {
        new TaskTypeSelectionScreen(parent_panel, task_items).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        if (this.task_items.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.chapter_task_select.empty"),
                    dev.ftb.mods.ftblibrary.icon.Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        for (ContextMenuItem task_item : this.task_items) {
            panel.add(new SimpleTextButton(panel, task_item.getTitle(), task_item.getIcon()) {
                @Override
                public void onClicked(MouseButton button) {
                    if (button.isLeft()) {
                        SimpleTextButton action_button = new SimpleTextButton(
                                TaskTypeSelectionScreen.this.parent_panel,
                                task_item.getTitle(),
                                task_item.getIcon()
                        ) {
                            @Override
                            public void onClicked(MouseButton ignored_button) {
                            }
                        };
                        TaskTypeSelectionScreen.this.parent_panel.run();
                        task_item.onClicked(action_button, TaskTypeSelectionScreen.this.parent_panel, button);
                    }
                }
            });
        }
    }

    @Override
    public boolean onInit() {
        int target_width = Mth.clamp(420, 176, Minecraft.getInstance().getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.task_items.size() * 20 + 50,
                166,
                Minecraft.getInstance().getWindow().getGuiScaledHeight() * 4 / 5
        );
        this.setSize(target_width, target_height);
        return super.onInit();
    }

    @Override
    public void onBack() {
        this.parent_panel.run();
    }

    @Override
    protected void doCancel() {
        this.parent_panel.run();
    }

    @Override
    protected void doAccept() {
        this.parent_panel.run();
    }
}
