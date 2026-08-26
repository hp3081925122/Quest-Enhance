package com.quest_enhance.client.description;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.client.gui.widget.Panel;
import dev.ftb.mods.ftblibrary.client.gui.widget.SimpleTextButton;
import dev.ftb.mods.ftblibrary.client.gui.screens.AbstractButtonListScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class KeybindSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final String current_keybind;
    private final Consumer<String> selection_callback;
    private final boolean multi_select;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<KeyMapping> key_mappings;

    private KeybindSelectionScreen(
            Panel parent_panel,
            String current_keybind,
            boolean multi_select,
            Consumer<String> selection_callback
    ) {
        this.parent_panel = parent_panel;
        this.current_keybind = current_keybind == null ? "" : current_keybind;
        this.multi_select = multi_select;
        this.selection_callback = selection_callback;
        // 编辑单个按键时预选当前项，插入模式从空集合开始
        if (!this.current_keybind.isEmpty()) {
            this.selected.add(this.current_keybind);
        }
        // 收集当前游戏所有已注册的按键绑定，去重后供搜索列表选择
        this.key_mappings = new ArrayList<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            String name = mapping.getName();
            boolean duplicate = false;
            for (KeyMapping existing : this.key_mappings) {
                if (existing.getName().equals(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                this.key_mappings.add(mapping);
            }
        }
        if (multi_select) {
            this.setTitle(Component.translatable("quest_enhance.description_component.keybind.select.multi"));
        } else {
            this.setTitle(Component.translatable("quest_enhance.description_component.keybind.select"));
        }
        this.setHasSearchBox(true);
        // 单选模式沿用即时确认，无需底部确认按钮
        this.showBottomPanel(multi_select);
    }

    // 插入模式：开启多选，勾选后由底部确认按钮批量插入
    public static void open(Panel parent_panel, String current_keybind, Consumer<String> selection_callback) {
        new KeybindSelectionScreen(parent_panel, current_keybind, true, selection_callback).openGui();
    }

    // 编辑模式：保持单选即时确认，避免一次替换成多个按键
    public static void openSingle(Panel parent_panel, String current_keybind, Consumer<String> selection_callback) {
        new KeybindSelectionScreen(parent_panel, current_keybind, false, selection_callback).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        if (this.key_mappings.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.description_component.keybind.empty"),
                    Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        // 每条显示翻译名并附技术键，便于按名称或 key.xxx 搜索过滤
        for (KeyMapping mapping : this.key_mappings) {
            String name = mapping.getName();
            Component translated = Component.translatable(name);
            Component with_key = translated.copy().append(
                    Component.literal(" (" + name + ")").withStyle(ChatFormatting.DARK_GRAY)
            );
            Component label;
            if (multi_select) {
                String mark = selected.contains(name) ? "[x] " : "[ ] ";
                label = Component.literal(mark).withStyle(ChatFormatting.GREEN).append(with_key);
            } else {
                label = name.equals(this.current_keybind)
                        ? Component.translatable("quest_enhance.description_component.keybind.selected", with_key)
                        : with_key;
            }
            panel.add(SimpleTextButton.create(
                    panel,
                    label,
                    Icons.CONTROLLER,
                    button -> {
                        if (multi_select) {
                            // 多选：点击仅切换勾选状态并刷新列表，不立即关闭
                            if (selected.contains(name)) {
                                selected.remove(name);
                            } else {
                                selected.add(name);
                            }
                            mainPanel.refreshWidgets();
                        } else {
                            // 单选：即时确认当前按键
                            this.parent_panel.run();
                            this.selection_callback.accept(name);
                        }
                    }
            ));
        }
    }

    @Override
    public boolean onInit() {
        int target_width = Mth.clamp(420, 176, this.getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.key_mappings.size() * 20 + 50,
                166,
                this.getWindow().getGuiScaledHeight() * 4 / 5
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
        if (!multi_select) {
            this.parent_panel.run();
            return;
        }
        // 多选确认：每个勾选的按键单独占一行插入描述
        if (selected.isEmpty()) {
            this.parent_panel.run();
            return;
        }
        List<String> chosen = new ArrayList<>(selected);
        this.parent_panel.run();
        for (String keybind : chosen) {
            this.selection_callback.accept(keybind);
        }
    }
}
