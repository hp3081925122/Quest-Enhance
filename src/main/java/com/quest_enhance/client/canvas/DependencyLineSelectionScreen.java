package com.quest_enhance.client.canvas;

import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

// 显示与当前任务存在前置关系的全部任务节点
public final class DependencyLineSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final List<ContextMenuItem> line_items;

    private DependencyLineSelectionScreen(Panel parent_panel, List<ContextMenuItem> line_items) {
        this.parent_panel = parent_panel;
        this.line_items = List.copyOf(line_items);
        this.setTitle(Component.translatable("quest_enhance.dependency_line.select"));
        this.setHasSearchBox(true);
    }

    // 打开前置线关联节点的选择列表
    public static void open(Panel parent_panel, List<ContextMenuItem> line_items) {
        new DependencyLineSelectionScreen(parent_panel, line_items).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        if (this.line_items.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.dependency_line.select.empty"),
                    dev.ftb.mods.ftblibrary.icon.Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        for (ContextMenuItem line_item : this.line_items) {
            panel.add(new SimpleTextButton(panel, line_item.getTitle(), line_item.getIcon()) {
                @Override
                public void onClicked(MouseButton button) {
                    if (button.isLeft()) {
                        SimpleTextButton action_button = new SimpleTextButton(
                                DependencyLineSelectionScreen.this.parent_panel,
                                line_item.getTitle(),
                                line_item.getIcon()
                        ) {
                            @Override
                            public void onClicked(MouseButton ignored_button) {
                            }
                        };
                        DependencyLineSelectionScreen.this.parent_panel.run();
                        line_item.onClicked(action_button, DependencyLineSelectionScreen.this.parent_panel, button);
                    }
                }
            });
        }
    }

    @Override
    public boolean onInit() {
        int target_width = Mth.clamp(420, 176, Minecraft.getInstance().getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.line_items.size() * 20 + 50,
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
