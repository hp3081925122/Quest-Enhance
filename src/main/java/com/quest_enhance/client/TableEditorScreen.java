package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public final class TableEditorScreen extends BaseScreen {
    private static final int TABLE_TOP = 80;
    private static final int FOOTER_HEIGHT = 30;

    private final Panel parent_panel;
    private final Consumer<String> save;
    private final List<List<String>> rows;
    private int columns;
    private boolean header;
    private QuestDescriptionTable.TextAlignment alignment;
    private int table_width;
    private int row_height;
    private int line_width;
    private Color4I border_color;
    private Color4I header_color;
    private Color4I cell_color;
    private Color4I text_color;
    private int editing_row = -1;
    private int editing_column = -1;
    private int table_scroll;
    private boolean loading_cell_editor;
    private boolean cell_edit_changed;
    private final Deque<QuestDescriptionTable.TableData> undo_history = new ArrayDeque<>();
    private final Deque<QuestDescriptionTable.TableData> redo_history = new ArrayDeque<>();
    private TextBox cell_editor;
    private SimpleTextButton add_row_button;
    private SimpleTextButton remove_row_button;
    private SimpleTextButton add_column_button;
    private SimpleTextButton remove_column_button;
    private SimpleTextButton settings_button;
    private SimpleTextButton undo_button;
    private SimpleTextButton redo_button;
    private SimpleTextButton accept_button;
    private SimpleTextButton cancel_button;

    private TableEditorScreen(
            Panel parent_panel,
            QuestDescriptionTable.TableData initial,
            Consumer<String> save
    ) {
        this.parent_panel = parent_panel;
        this.save = save;
        this.columns = initial.columns();
        this.rows = new ArrayList<>(initial.rows().size());
        for (List<String> row : initial.rows()) {
            this.rows.add(new ArrayList<>(row));
        }
        this.header = initial.header();
        this.alignment = initial.alignment();
        this.table_width = initial.tableWidth();
        this.row_height = initial.rowHeight();
        this.line_width = initial.lineWidth();
        this.border_color = initial.borderColor();
        this.header_color = initial.headerColor();
        this.cell_color = initial.cellColor();
        this.text_color = initial.textColor();
        this.setRenderBlur(true);
    }

    // 打开表格预览编辑器并保留原描述编辑界面作为返回目标
    public static void open(
            Panel parent_panel,
            QuestDescriptionTable.TableData initial,
            Consumer<String> save
    ) {
        new TableEditorScreen(parent_panel, initial, save).openGui();
    }

    @Override
    public boolean onInit() {
        int maximum_width = Math.max(420, this.getWindow().getGuiScaledWidth() - 20);
        int maximum_height = Math.max(240, this.getWindow().getGuiScaledHeight() - 20);
        this.setSize(
                Mth.clamp(640, 420, maximum_width),
                Mth.clamp(480, 240, maximum_height)
        );
        return super.onInit();
    }

    @Override
    public void addWidgets() {
        // 单元格输入框只在单击选择单元格后显示并实时写回表格数据
        this.cell_editor = new TextBox(this) {
            @Override
            public boolean shouldDraw() {
                return TableEditorScreen.this.editing_row >= 0;
            }

            @Override
            public boolean isEnabled() {
                return TableEditorScreen.this.editing_row >= 0;
            }

            @Override
            public void onTextChanged() {
                super.onTextChanged();
                if (TableEditorScreen.this.editing_row >= 0
                        && TableEditorScreen.this.editing_row < TableEditorScreen.this.rows.size()
                        && TableEditorScreen.this.editing_column >= 0
                        && TableEditorScreen.this.editing_column < TableEditorScreen.this.columns) {
                    if (!TableEditorScreen.this.loading_cell_editor
                            && !TableEditorScreen.this.cell_edit_changed) {
                        TableEditorScreen.this.pushUndo(TableEditorScreen.this.tableData());
                        TableEditorScreen.this.cell_edit_changed = true;
                    }
                    TableEditorScreen.this.rows
                            .get(TableEditorScreen.this.editing_row)
                            .set(TableEditorScreen.this.editing_column, this.getText());
                }
            }

            @Override
            public void onEnterPressed() {
                TableEditorScreen.this.finishCellEdit();
            }
        };
        this.cell_editor.setMaxLength(QuestDescriptionTable.MAX_CELL_LENGTH);
        this.cell_editor.setFilter(value -> !value.contains("\r") && !value.contains("\n"));
        this.add(this.cell_editor);

        // 表格结构按钮使用明确的本地化文字区分行列操作
        this.add_row_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.add_row"),
                Icons.ADD,
                button -> {
                    if (button.isLeft() && this.rows.size() < QuestDescriptionTable.MAX_ROWS) {
                        this.finishCellEdit();
                        this.pushUndo(this.tableData());
                        this.rows.add(new ArrayList<>(java.util.Collections.nCopies(this.columns, "")));
                    }
                }
        );
        this.remove_row_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.remove_row"),
                Icons.REMOVE,
                button -> {
                    if (button.isLeft() && this.rows.size() > 1) {
                        int row_to_remove = this.editing_row >= 0
                                ? this.editing_row
                                : this.rows.size() - 1;
                        this.finishCellEdit();
                        this.pushUndo(this.tableData());
                        this.rows.remove(row_to_remove);
                    }
                }
        );
        this.add_column_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.add_column"),
                Icons.ADD,
                button -> {
                    if (button.isLeft() && this.columns < QuestDescriptionTable.MAX_COLUMNS) {
                        this.finishCellEdit();
                        this.pushUndo(this.tableData());
                        this.columns++;
                        this.rows.forEach(row -> row.add(""));
                    }
                }
        );
        this.remove_column_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.remove_column"),
                Icons.REMOVE,
                button -> {
                    if (button.isLeft() && this.columns > 1) {
                        int column_to_remove = this.editing_column >= 0
                                ? this.editing_column
                                : this.columns - 1;
                        this.finishCellEdit();
                        this.pushUndo(this.tableData());
                        this.columns--;
                        this.rows.forEach(row -> row.remove(column_to_remove));
                    }
                }
        );
        this.settings_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.settings"),
                Icons.SETTINGS,
                button -> {
                    if (button.isLeft()) {
                        this.finishCellEdit();
                        this.openStyleConfig();
                    }
                }
        );

        // 底部提供与快捷键共用的撤销和重做操作
        this.undo_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.undo"),
                Icons.LEFT,
                button -> {
                    if (button.isLeft()) {
                        this.undo();
                    }
                }
        );
        this.redo_button = SimpleTextButton.create(
                this,
                Component.translatable("quest_enhance.description_component.table.redo"),
                Icons.RIGHT,
                button -> {
                    if (button.isLeft()) {
                        this.redo();
                    }
                }
        );

        // 底部确认和取消按钮与 FTB 原生编辑界面的返回行为保持一致
        this.accept_button = SimpleTextButton.create(
                this,
                Component.translatable("gui.done"),
                Icons.ACCEPT,
                button -> {
                    if (button.isLeft()) {
                        this.finishCellEdit();
                        this.save.accept(QuestDescriptionTable.createMarkup(this.tableData()));
                        this.parent_panel.run();
                    }
                }
        );
        this.cancel_button = SimpleTextButton.create(
                this,
                Component.translatable("gui.cancel"),
                Icons.CANCEL,
                button -> {
                    if (button.isLeft()) {
                        this.parent_panel.run();
                    }
                }
        );
        this.add(this.add_row_button);
        this.add(this.remove_row_button);
        this.add(this.add_column_button);
        this.add(this.remove_column_button);
        this.add(this.settings_button);
        this.add(this.undo_button);
        this.add(this.redo_button);
        this.add(this.accept_button);
        this.add(this.cancel_button);
    }

    @Override
    public void alignWidgets() {
        int button_width = 68;
        this.add_row_button.setPosAndSize(8, 36, button_width, 18);
        this.remove_row_button.setPosAndSize(78, 36, button_width, 18);
        this.add_column_button.setPosAndSize(148, 36, button_width, 18);
        this.remove_column_button.setPosAndSize(218, 36, button_width, 18);
        this.settings_button.setPosAndSize(this.width - 92, 36, 84, 18);
        this.cell_editor.setPosAndSize(148, 58, this.width - 156, 18);
        this.undo_button.setPosAndSize(8, this.height - 24, 68, 18);
        this.redo_button.setPosAndSize(78, this.height - 24, 68, 18);
        this.accept_button.setPosAndSize(this.width - 172, this.height - 24, 80, 18);
        this.cancel_button.setPosAndSize(this.width - 88, this.height - 24, 80, 18);
    }

    @Override
    public void drawBackground(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height
    ) {
        theme.drawPanelBackground(graphics, x, y, width, height);
        Component title = Component.translatable("quest_enhance.description_component.table");
        theme.drawString(
                graphics,
                title,
                x + (width - theme.getStringWidth(title)) / 2,
                y + 8,
                Color4I.WHITE,
                0
        );
        theme.drawString(
                graphics,
                Component.translatable("quest_enhance.description_component.table.edit_hint"),
                x + 8,
                y + 22,
                Color4I.GRAY,
                0
        );

        // 单击后在表格上方显示当前单元格位置和单行输入框
        if (this.editing_row >= 0) {
            theme.drawString(
                    graphics,
                    Component.translatable(
                            "quest_enhance.description_component.table.editing_cell",
                            this.editing_row + 1,
                            this.editing_column + 1
                    ),
                    x + 8,
                    y + 63,
                    Color4I.WHITE,
                    0
            );
        }

        // 表格区域使用裁剪和滚动，长表格不会覆盖底部按钮
        QuestDescriptionTable.TableLayout layout = QuestDescriptionTable.layout(
                theme,
                width - 16,
                this.tableData()
        );
        int viewport_height = Math.max(1, height - TABLE_TOP - FOOTER_HEIGHT);
        this.table_scroll = Mth.clamp(
                this.table_scroll,
                0,
                Math.max(0, layout.height() - viewport_height)
        );
        graphics.enableScissor(
                x + 8,
                y + TABLE_TOP,
                x + width - 8,
                y + height - FOOTER_HEIGHT
        );
        QuestDescriptionTable.draw(
                graphics,
                theme,
                x + 8,
                y + TABLE_TOP - this.table_scroll,
                width - 16,
                this.tableData()
        );
        graphics.disableScissor();
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        if (!button.isLeft()) {
            return super.mousePressed(button);
        }

        // 将单击位置换算为滚动后表格中的行列坐标
        int table_x = this.getX() + 8;
        int viewport_y = this.getY() + TABLE_TOP;
        int viewport_bottom = this.getY() + this.height - FOOTER_HEIGHT;
        QuestDescriptionTable.TableLayout layout = QuestDescriptionTable.layout(
                this.getTheme(),
                this.width - 16,
                this.tableData()
        );
        int mouse_x = this.getMouseX();
        int mouse_y = this.getMouseY();
        if (mouse_x < table_x
                || mouse_x >= table_x + layout.width()
                || mouse_y < viewport_y
                || mouse_y >= viewport_bottom) {
            return super.mousePressed(button);
        }

        int content_y = mouse_y - viewport_y + this.table_scroll;
        int row_top = 0;
        for (int row = 0; row < layout.rowHeights().size(); row++) {
            int row_bottom = row_top + layout.rowHeights().get(row);
            if (content_y >= row_top && content_y < row_bottom) {
                int column = Math.min(
                        this.columns - 1,
                        Math.max(0, (mouse_x - table_x) * this.columns / layout.width())
                );
                this.beginCellEdit(row, column);
                return true;
            }
            row_top = row_bottom;
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseScrolled(double scroll) {
        int viewport_y = this.getY() + TABLE_TOP;
        int viewport_bottom = this.getY() + this.height - FOOTER_HEIGHT;
        if (this.getMouseY() >= viewport_y && this.getMouseY() < viewport_bottom) {
            QuestDescriptionTable.TableLayout layout = QuestDescriptionTable.layout(
                    this.getTheme(),
                    this.width - 16,
                    this.tableData()
            );
            int viewport_height = Math.max(1, this.height - TABLE_TOP - FOOTER_HEIGHT);
            this.table_scroll = Mth.clamp(
                    this.table_scroll - (int) Math.round(scroll * 18.0D),
                    0,
                    Math.max(0, layout.height() - viewport_height)
            );
            return true;
        }
        return super.mouseScrolled(scroll);
    }

    @Override
    public boolean keyPressed(Key key) {
        // 在输入框聚焦时也优先处理整张表格的撤销和重做快捷键
        if (Widget.isCtrlKeyDown() && key.is(GLFW.GLFW_KEY_Z)) {
            if (Widget.isShiftKeyDown()) {
                this.redo();
            } else {
                this.undo();
            }
            return true;
        }
        if (Widget.isCtrlKeyDown() && key.is(GLFW.GLFW_KEY_Y)) {
            this.redo();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void onBack() {
        this.parent_panel.run();
    }

    // 单击单元格后载入内容、全选文字并把键盘焦点交给输入框
    private void beginCellEdit(int row, int column) {
        this.finishCellEdit();
        this.editing_row = row;
        this.editing_column = column;
        String value = this.rows.get(row).get(column);
        this.loading_cell_editor = true;
        this.cell_editor.setText(value);
        this.loading_cell_editor = false;
        this.cell_edit_changed = false;
        this.cell_editor.setFocused(true);
        this.cell_editor.setCursorPos(value.length());
        this.cell_editor.setSelectionPos(0);
    }

    // 结束当前单元格编辑并隐藏输入框
    private void finishCellEdit() {
        if (this.cell_editor != null) {
            this.cell_editor.setFocused(false);
        }
        this.editing_row = -1;
        this.editing_column = -1;
        this.loading_cell_editor = false;
        this.cell_edit_changed = false;
    }

    // 打开不包含原始行字符串的表格样式设置页
    private void openStyleConfig() {
        boolean[] next_header = {this.header};
        QuestDescriptionTable.TextAlignment[] next_alignment = {this.alignment};
        int[] next_table_width = {this.table_width};
        int[] next_row_height = {this.row_height};
        int[] next_line_width = {this.line_width};
        Color4I[] next_border_color = {this.border_color};
        Color4I[] next_header_color = {this.header_color};
        Color4I[] next_cell_color = {this.cell_color};
        Color4I[] next_text_color = {this.text_color};

        // 确认样式设置后再应用，取消不会改变当前表格
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                QuestDescriptionTable.TableData previous = this.tableData();
                this.header = next_header[0];
                this.alignment = next_alignment[0];
                this.table_width = next_table_width[0];
                this.row_height = next_row_height[0];
                this.line_width = next_line_width[0];
                this.border_color = next_border_color[0];
                this.header_color = next_header_color[0];
                this.cell_color = next_cell_color[0];
                this.text_color = next_text_color[0];
                this.table_scroll = 0;
                if (!previous.equals(this.tableData())) {
                    this.pushUndo(previous);
                }
            }
            this.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.table.settings");
            }
        };
        group.addInt("table_width", next_table_width[0], value -> next_table_width[0] = value, 0, 0, 1000)
                .setNameKey("quest_enhance.description_component.table.table_width");
        group.addBool("header", next_header[0], value -> next_header[0] = value, true)
                .setNameKey("quest_enhance.description_component.table.header");
        group.addEnum(
                "alignment",
                next_alignment[0],
                value -> next_alignment[0] = value,
                QuestDescriptionTable.TextAlignment.NAME_MAP,
                QuestDescriptionTable.TextAlignment.LEFT
        ).setNameKey("quest_enhance.description_component.table.alignment");
        group.addInt("row_height", next_row_height[0], value -> next_row_height[0] = value, 18, 12, 30)
                .setNameKey("quest_enhance.description_component.table.row_height");
        group.addInt("line_width", next_line_width[0], value -> next_line_width[0] = value, 1, 1, 4)
                .setNameKey("quest_enhance.description_component.table.line_width");
        group.addColor("border_color", next_border_color[0], value -> next_border_color[0] = value, Color4I.GRAY)
                .setNameKey("quest_enhance.description_component.table.border_color");
        group.addColor("header_color", next_header_color[0], value -> next_header_color[0] = value, Color4I.rgb(0x374151))
                .setNameKey("quest_enhance.description_component.table.header_color");
        group.addColor("cell_color", next_cell_color[0], value -> next_cell_color[0] = value, Color4I.rgb(0x1F2937))
                .setNameKey("quest_enhance.description_component.table.cell_color");
        group.addColor("text_color", next_text_color[0], value -> next_text_color[0] = value, Color4I.WHITE)
                .setNameKey("quest_enhance.description_component.table.text_color");
        new EditConfigScreen(group).openGui();
    }

    // 将修改前状态加入撤销栈，并在新修改产生时清空重做栈
    private void pushUndo(QuestDescriptionTable.TableData data) {
        if (this.undo_history.isEmpty() || !this.undo_history.peekLast().equals(data)) {
            this.undo_history.addLast(data);
            while (this.undo_history.size() > 64) {
                this.undo_history.removeFirst();
            }
        }
        this.redo_history.clear();
    }

    // 恢复上一个表格状态，并保存当前状态用于重做
    private void undo() {
        if (this.undo_history.isEmpty()) {
            return;
        }
        this.finishCellEdit();
        this.redo_history.addLast(this.tableData());
        this.applyData(this.undo_history.removeLast());
    }

    // 恢复最近一次被撤销的表格状态
    private void redo() {
        if (this.redo_history.isEmpty()) {
            return;
        }
        this.finishCellEdit();
        this.undo_history.addLast(this.tableData());
        this.applyData(this.redo_history.removeLast());
    }

    // 把不可变历史快照完整恢复到当前可编辑状态
    private void applyData(QuestDescriptionTable.TableData data) {
        this.columns = data.columns();
        this.rows.clear();
        for (List<String> row : data.rows()) {
            this.rows.add(new ArrayList<>(row));
        }
        this.header = data.header();
        this.alignment = data.alignment();
        this.table_width = data.tableWidth();
        this.row_height = data.rowHeight();
        this.line_width = data.lineWidth();
        this.border_color = data.borderColor();
        this.header_color = data.headerColor();
        this.cell_color = data.cellColor();
        this.text_color = data.textColor();
        this.table_scroll = 0;
    }

    // 从当前可变编辑状态生成用于预览和保存的不可变表格数据
    private QuestDescriptionTable.TableData tableData() {
        List<List<String>> immutable_rows = this.rows.stream().map(List::copyOf).toList();
        return new QuestDescriptionTable.TableData(
                this.columns,
                immutable_rows,
                this.header,
                this.alignment,
                this.table_width,
                this.row_height,
                this.line_width,
                this.border_color,
                this.header_color,
                this.cell_color,
                this.text_color
        );
    }
}
