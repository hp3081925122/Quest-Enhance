package com.quest_enhance.client.description;

import com.quest_enhance.QuestEnhance;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
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
    private final List<QuestDescriptionTable.CellMerge> merges;
    private final List<QuestDescriptionTable.CellStyle> cell_styles;
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
    private int selection_start_row = -1;
    private int selection_start_column = -1;
    private int selection_end_row = -1;
    private int selection_end_column = -1;
    private boolean selecting_cells;
    private int table_scroll;
    private boolean loading_cell_editor;
    private boolean cell_edit_changed;
    private boolean direct_cell_editor_visible;
    private boolean cell_context_menu_open;
    private final Deque<QuestDescriptionTable.TableData> undo_history = new ArrayDeque<>();
    private final Deque<QuestDescriptionTable.TableData> redo_history = new ArrayDeque<>();
    private TextBox cell_editor;
    private TextBox direct_cell_editor;
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
        this.merges = new ArrayList<>(initial.merges());
        this.cell_styles = new ArrayList<>(initial.cellStyles());
        this.header = initial.header();
        this.alignment = initial.alignment();
        this.table_width = initial.tableWidth();
        this.row_height = initial.rowHeight();
        this.line_width = initial.lineWidth();
        this.border_color = initial.borderColor();
        this.header_color = initial.headerColor();
        this.cell_color = initial.cellColor();
        this.text_color = initial.textColor();
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
        int maximum_width = Math.max(420, this.getScreen().getGuiScaledWidth() - 20);
        int maximum_height = Math.max(240, this.getScreen().getGuiScaledHeight() - 20);
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
                TableEditorScreen.this.onCellEditorChanged(this);
            }

            @Override
            public void onEnterPressed() {
                TableEditorScreen.this.finishCellEdit();
            }
        };
        this.cell_editor.setMaxLength(QuestDescriptionTable.MAX_CELL_LENGTH);
        this.cell_editor.setFilter(value -> !value.contains("\r") && !value.contains("\n"));
        this.add(this.cell_editor);

        // 选中单元格时在单元格内部显示可直接输入的文本框
        this.direct_cell_editor = new TextBox(this) {
            @Override
            public boolean shouldDraw() {
                return TableEditorScreen.this.direct_cell_editor_visible;
            }

            @Override
            public boolean isEnabled() {
                return TableEditorScreen.this.direct_cell_editor_visible;
            }

            @Override
            public void onTextChanged() {
                super.onTextChanged();
                TableEditorScreen.this.onCellEditorChanged(this);
            }

            @Override
            public void onEnterPressed() {
                TableEditorScreen.this.finishCellEdit();
            }
        };
        this.direct_cell_editor.setMaxLength(QuestDescriptionTable.MAX_CELL_LENGTH);
        this.direct_cell_editor.setFilter(value -> !value.contains("\r") && !value.contains("\n"));
        this.add(this.direct_cell_editor);

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
        this.add(this.undo_button);
        this.add(this.redo_button);
        this.add(this.accept_button);
        this.add(this.cancel_button);
    }

    @Override
    public void alignWidgets() {
        this.cell_editor.setPosAndSize(8, 36, this.width - 16, 18);
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
        this.updateDirectCellEditorPosition(layout);
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
        this.drawSelection(graphics, layout, x + 8, y + TABLE_TOP - this.table_scroll);
        graphics.disableScissor();
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        // 先让 FTB 模态菜单处理输入，菜单关闭后的这一次落空事件也必须消费。
        if (this.cell_context_menu_open) {
            boolean handled = super.mousePressed(button);
            if (!handled) {
                this.cell_context_menu_open = false;
                this.selecting_cells = false;
            }
            return true;
        }
        if (button.isRight()) {
            QuestDescriptionTable.TableLayout layout = QuestDescriptionTable.layout(
                    this.getTheme(),
                    this.width - 16,
                    this.tableData()
            );
            int[] cell = this.cellAtMouse(layout);
            if (cell == null) {
                return super.mousePressed(button);
            }
            this.finishCellEdit();
            if (!this.isCellSelected(cell[0], cell[1])) {
                this.setSelection(cell[0], cell[1], cell[0], cell[1], false);
            }
            this.cell_context_menu_open = true;
            this.openCellContextMenu(cell[0], cell[1]);
            return true;
        }
        if (!button.isLeft()) {
            return super.mousePressed(button);
        }

        // 单元格输入框内部的点击交给文本框处理，避免每次点击都重置光标
        if (this.direct_cell_editor_visible
                && this.getMouseX() >= this.direct_cell_editor.getX()
                && this.getMouseX() < this.direct_cell_editor.getX() + this.direct_cell_editor.getWidth()
                && this.getMouseY() >= this.direct_cell_editor.getY()
                && this.getMouseY() < this.direct_cell_editor.getY() + this.direct_cell_editor.getHeight()) {
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

        int[] cell = this.cellAtMouse(layout);
        if (cell != null) {
            this.selecting_cells = true;
            this.setSelection(cell[0], cell[1], cell[0], cell[1]);
            return true;
        }
        return super.mousePressed(button);
    }

    @Override
    public boolean mouseDragged(int button, double delta_x, double delta_y) {
        // 右键菜单打开期间禁止背景表格进入选区拖拽。
        if (this.cell_context_menu_open) {
            super.mouseDragged(button, delta_x, delta_y);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && this.selecting_cells) {
            QuestDescriptionTable.TableLayout layout = QuestDescriptionTable.layout(
                    this.getTheme(),
                    this.width - 16,
                    this.tableData()
            );
            int[] cell = this.cellAtMouse(layout);
            if (cell != null) {
                this.setSelection(
                        this.selection_start_row,
                        this.selection_start_column,
                        cell[0],
                        cell[1],
                        false
                );
            }
            return true;
        }
        return super.mouseDragged(button, delta_x, delta_y);
    }

    @Override
    public void mouseReleased(MouseButton button) {
        this.selecting_cells = false;
        if (this.cell_context_menu_open) {
            super.mouseReleased(button);
            return;
        }
        super.mouseReleased(button);
    }

    @Override
    public boolean mouseScrolled(double scroll) {
        // 菜单打开期间滚轮只交给菜单，避免背景表格同步滚动。
        if (this.cell_context_menu_open) {
            super.mouseScrolled(scroll);
            return true;
        }
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

    // 判断右键位置是否仍在当前拖拽选区内，保留多选后右键合并的操作上下文
    private boolean isCellSelected(int row, int column) {
        if (this.selection_start_row < 0 || this.selection_end_row < 0) {
            return false;
        }
        return row >= Math.min(this.selection_start_row, this.selection_end_row)
                && row <= Math.max(this.selection_start_row, this.selection_end_row)
                && column >= Math.min(this.selection_start_column, this.selection_end_column)
                && column <= Math.max(this.selection_start_column, this.selection_end_column);
    }

    // 根据右键位置和当前选区创建表格结构操作菜单
    private void openCellContextMenu(int context_row, int context_column) {
        QuestDescriptionTable.TableData data = this.tableData();
        QuestDescriptionTable.CellMerge context_merge = QuestDescriptionTable.mergeAt(
                data,
                context_row,
                context_column
        );
        List<ContextMenuItem> add_row_items = List.of(
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.table.add_row_above"),
                        Icons.UP,
                        button -> {
                            this.cell_context_menu_open = false;
                            this.insertRow(context_row, context_column, true);
                        }
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.table.add_row_below"),
                        Icons.DOWN,
                        button -> {
                            this.cell_context_menu_open = false;
                            this.insertRow(context_row, context_column, false);
                        }
                )
        );
        List<ContextMenuItem> add_column_items = List.of(
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.table.add_column_left"),
                        Icons.LEFT,
                        button -> {
                            this.cell_context_menu_open = false;
                            this.insertColumn(context_row, context_column, true);
                        }
                ),
                new ContextMenuItem(
                        Component.translatable("quest_enhance.description_component.table.add_column_right"),
                        Icons.RIGHT,
                        button -> {
                            this.cell_context_menu_open = false;
                            this.insertColumn(context_row, context_column, false);
                        }
                )
        );
        List<ContextMenuItem> menu = new ArrayList<>();
        menu.add(ContextMenuItem.subMenu(
                Component.translatable("quest_enhance.description_component.table.add_row"),
                Icons.ADD,
                add_row_items
        ).setEnabled(this.rows.size() < QuestDescriptionTable.MAX_ROWS));
        menu.add(new ContextMenuItem(
                Component.translatable("quest_enhance.description_component.table.remove_row"),
                Icons.REMOVE,
                button -> {
                    this.cell_context_menu_open = false;
                    this.removeRow(context_row, context_column);
                }
        ).setEnabled(this.rows.size() > 1));
        menu.add(ContextMenuItem.separator());
        menu.add(ContextMenuItem.subMenu(
                Component.translatable("quest_enhance.description_component.table.add_column"),
                Icons.ADD,
                add_column_items
        ).setEnabled(this.columns < QuestDescriptionTable.MAX_COLUMNS));
        menu.add(new ContextMenuItem(
                Component.translatable("quest_enhance.description_component.table.remove_column"),
                Icons.REMOVE,
                button -> {
                    this.cell_context_menu_open = false;
                    this.removeColumn(context_row, context_column);
                }
        ).setEnabled(this.columns > 1));
        menu.add(ContextMenuItem.separator());
        if (context_merge != null) {
            menu.add(new ContextMenuItem(
                    Component.translatable("quest_enhance.description_component.table.split"),
                    Icons.REMOVE,
                    button -> {
                        this.cell_context_menu_open = false;
                        this.splitSelection();
                    }
            ));
        } else if (this.canMergeSelection()) {
            menu.add(new ContextMenuItem(
                    Component.translatable("quest_enhance.description_component.table.merge"),
                    Icons.ADD,
                    button -> {
                        this.cell_context_menu_open = false;
                        this.mergeSelection();
                    }
            ));
        }
        menu.add(ContextMenuItem.separator());
        menu.add(new ContextMenuItem(
                Component.translatable("quest_enhance.description_component.table.cell_settings"),
                Icons.SETTINGS,
                button -> {
                    this.cell_context_menu_open = false;
                    this.finishCellEdit();
                    this.openCellConfig(context_row, context_column);
                }
        ));
        this.openContextMenu(menu);
    }

    // 在指定单元格所在行的上方或下方插入新行，并同步调整合并区域
    private void insertRow(int context_row, int context_column, boolean before) {
        if (this.rows.size() >= QuestDescriptionTable.MAX_ROWS) {
            return;
        }
        QuestDescriptionTable.CellMerge context_merge = QuestDescriptionTable.mergeAt(
                this.tableData(),
                context_row,
                context_column
        );
        int insertion_row = before
                ? context_merge == null ? context_row : context_merge.row()
                : context_merge == null
                ? context_row + 1
                : context_merge.row() + context_merge.rowSpan();
        this.finishCellEdit();
        this.pushUndo(this.tableData());
        int inserted_row = Mth.clamp(insertion_row, 0, this.rows.size());
        int style_source_row = Mth.clamp(context_row, 0, this.rows.size() - 1);
        List<QuestDescriptionTable.CellStyle> inserted_styles = new ArrayList<>(this.columns);
        for (int column = 0; column < this.columns; column++) {
            inserted_styles.add(this.cell_styles.get(style_source_row * this.columns + column));
        }
        this.rows.add(
                inserted_row,
                new ArrayList<>(java.util.Collections.nCopies(this.columns, ""))
        );
        this.cell_styles.addAll(inserted_row * this.columns, inserted_styles);
        List<QuestDescriptionTable.CellMerge> adjusted_merges = new ArrayList<>();
        for (QuestDescriptionTable.CellMerge merge : this.merges) {
            if (merge.row() >= insertion_row) {
                adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                        merge.row() + 1,
                        merge.column(),
                        merge.rowSpan(),
                        merge.columnSpan()
                ));
            } else if (insertion_row <= merge.row() + merge.rowSpan() - 1) {
                adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                        merge.row(),
                        merge.column(),
                        merge.rowSpan() + 1,
                        merge.columnSpan()
                ));
            } else {
                adjusted_merges.add(merge);
            }
        }
        this.merges.clear();
        this.merges.addAll(adjusted_merges);
        // 插入后继续高亮原来的单元格，避免高亮新建的空行。
        int selected_row = before ? context_row + 1 : context_row;
        this.setSelection(
                Mth.clamp(selected_row, 0, this.rows.size() - 1),
                Mth.clamp(context_column, 0, this.columns - 1),
                Mth.clamp(selected_row, 0, this.rows.size() - 1),
                Mth.clamp(context_column, 0, this.columns - 1),
                false
        );
    }

    // 删除指定单元格所在行，并让跨行合并区域缩小而不是直接丢失
    private void removeRow(int context_row, int context_column) {
        if (this.rows.size() <= 1) {
            return;
        }
        int row_to_remove = Mth.clamp(context_row, 0, this.rows.size() - 1);
        this.finishCellEdit();
        this.pushUndo(this.tableData());
        this.rows.remove(row_to_remove);
        for (int index = 0; index < this.columns; index++) {
            this.cell_styles.remove(row_to_remove * this.columns);
        }
        List<QuestDescriptionTable.CellMerge> adjusted_merges = new ArrayList<>();
        for (QuestDescriptionTable.CellMerge merge : this.merges) {
            int merge_end = merge.row() + merge.rowSpan() - 1;
            if (merge.row() > row_to_remove) {
                adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                        merge.row() - 1,
                        merge.column(),
                        merge.rowSpan(),
                        merge.columnSpan()
                ));
            } else if (row_to_remove >= merge.row() && row_to_remove <= merge_end) {
                if (merge.rowSpan() > 1) {
                    adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                            merge.row(),
                            merge.column(),
                            merge.rowSpan() - 1,
                            merge.columnSpan()
                    ));
                }
            } else {
                adjusted_merges.add(merge);
            }
        }
        this.merges.clear();
        this.merges.addAll(adjusted_merges);
        int selected_row = Math.min(row_to_remove, this.rows.size() - 1);
        this.setSelection(
                selected_row,
                Mth.clamp(context_column, 0, this.columns - 1),
                selected_row,
                Mth.clamp(context_column, 0, this.columns - 1),
                false
        );
    }

    // 在指定单元格所在列的左侧或右侧插入新列，并同步调整合并区域
    private void insertColumn(int context_row, int context_column, boolean before) {
        if (this.columns >= QuestDescriptionTable.MAX_COLUMNS) {
            return;
        }
        QuestDescriptionTable.CellMerge context_merge = QuestDescriptionTable.mergeAt(
                this.tableData(),
                context_row,
                context_column
        );
        int insertion_column = before
                ? context_merge == null ? context_column : context_merge.column()
                : context_merge == null
                ? context_column + 1
                : context_merge.column() + context_merge.columnSpan();
        this.finishCellEdit();
        this.pushUndo(this.tableData());
        int previous_columns = this.columns;
        int inserted_column = Mth.clamp(insertion_column, 0, previous_columns);
        int style_source_column = Mth.clamp(context_column, 0, previous_columns - 1);
        List<QuestDescriptionTable.CellStyle> adjusted_styles = new ArrayList<>(
                this.rows.size() * (previous_columns + 1)
        );
        for (int row = 0; row < this.rows.size(); row++) {
            for (int column = 0; column <= previous_columns; column++) {
                if (column == inserted_column) {
                    adjusted_styles.add(this.cell_styles.get(row * previous_columns + style_source_column));
                }
                if (column < previous_columns) {
                    adjusted_styles.add(this.cell_styles.get(row * previous_columns + column));
                }
            }
        }
        this.columns++;
        this.rows.forEach(row -> row.add(inserted_column, ""));
        this.cell_styles.clear();
        this.cell_styles.addAll(adjusted_styles);
        List<QuestDescriptionTable.CellMerge> adjusted_merges = new ArrayList<>();
        for (QuestDescriptionTable.CellMerge merge : this.merges) {
            if (merge.column() >= inserted_column) {
                adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                        merge.row(),
                        merge.column() + 1,
                        merge.rowSpan(),
                        merge.columnSpan()
                ));
            } else if (inserted_column <= merge.column() + merge.columnSpan() - 1) {
                adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                        merge.row(),
                        merge.column(),
                        merge.rowSpan(),
                        merge.columnSpan() + 1
                ));
            } else {
                adjusted_merges.add(merge);
            }
        }
        this.merges.clear();
        this.merges.addAll(adjusted_merges);
        // 插入后继续高亮原来的单元格，避免高亮新建的空列。
        int selected_column = before ? context_column + 1 : context_column;
        this.setSelection(
                Mth.clamp(context_row, 0, this.rows.size() - 1),
                Mth.clamp(selected_column, 0, this.columns - 1),
                Mth.clamp(context_row, 0, this.rows.size() - 1),
                Mth.clamp(selected_column, 0, this.columns - 1),
                false
        );
    }

    // 删除指定单元格所在列，并让跨列合并区域缩小而不是直接丢失
    private void removeColumn(int context_row, int context_column) {
        if (this.columns <= 1) {
            return;
        }
        int column_to_remove = Mth.clamp(context_column, 0, this.columns - 1);
        this.finishCellEdit();
        this.pushUndo(this.tableData());
        List<QuestDescriptionTable.CellStyle> adjusted_styles = new ArrayList<>(
                this.rows.size() * (this.columns - 1)
        );
        for (int row = 0; row < this.rows.size(); row++) {
            for (int column = 0; column < this.columns; column++) {
                if (column != column_to_remove) {
                    adjusted_styles.add(this.cell_styles.get(row * this.columns + column));
                }
            }
        }
        this.columns--;
        this.rows.forEach(row -> row.remove(column_to_remove));
        this.cell_styles.clear();
        this.cell_styles.addAll(adjusted_styles);
        List<QuestDescriptionTable.CellMerge> adjusted_merges = new ArrayList<>();
        for (QuestDescriptionTable.CellMerge merge : this.merges) {
            int merge_end = merge.column() + merge.columnSpan() - 1;
            if (merge.column() > column_to_remove) {
                adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                        merge.row(),
                        merge.column() - 1,
                        merge.rowSpan(),
                        merge.columnSpan()
                ));
            } else if (column_to_remove >= merge.column() && column_to_remove <= merge_end) {
                if (merge.columnSpan() > 1) {
                    adjusted_merges.add(new QuestDescriptionTable.CellMerge(
                            merge.row(),
                            merge.column(),
                            merge.rowSpan(),
                            merge.columnSpan() - 1
                    ));
                }
            } else {
                adjusted_merges.add(merge);
            }
        }
        this.merges.clear();
        this.merges.addAll(adjusted_merges);
        int selected_column = Math.min(column_to_remove, this.columns - 1);
        this.setSelection(
                Mth.clamp(context_row, 0, this.rows.size() - 1),
                selected_column,
                Mth.clamp(context_row, 0, this.rows.size() - 1),
                selected_column,
                false
        );
    }

    // 判断当前多选区域是否可以完整合并，避免右键菜单出现无效操作
    private boolean canMergeSelection() {
        int top_row = Math.min(this.selection_start_row, this.selection_end_row);
        int bottom_row = Math.max(this.selection_start_row, this.selection_end_row);
        int left_column = Math.min(this.selection_start_column, this.selection_end_column);
        int right_column = Math.max(this.selection_start_column, this.selection_end_column);
        if (top_row < 0
                || left_column < 0
                || (top_row == bottom_row && left_column == right_column)) {
            return false;
        }
        for (QuestDescriptionTable.CellMerge merge : this.merges) {
            boolean overlaps = merge.row() <= bottom_row
                    && merge.row() + merge.rowSpan() - 1 >= top_row
                    && merge.column() <= right_column
                    && merge.column() + merge.columnSpan() - 1 >= left_column;
            boolean contained = merge.row() >= top_row
                    && merge.row() + merge.rowSpan() - 1 <= bottom_row
                    && merge.column() >= left_column
                    && merge.column() + merge.columnSpan() - 1 <= right_column;
            if (overlaps && !contained) {
                return false;
            }
        }
        return true;
    }

    // 将当前鼠标位置转换为表格内容中的普通单元格坐标
    private int[] cellAtMouse(QuestDescriptionTable.TableLayout layout) {
        int table_x = this.getX() + 8;
        int viewport_y = this.getY() + TABLE_TOP;
        int viewport_bottom = this.getY() + this.height - FOOTER_HEIGHT;
        int mouse_x = this.getMouseX();
        int mouse_y = this.getMouseY();
        if (mouse_x < table_x
                || mouse_x >= table_x + layout.width()
                || mouse_y < viewport_y
                || mouse_y >= viewport_bottom) {
            return null;
        }
        int content_y = mouse_y - viewport_y + this.table_scroll;
        int row_top = 0;
        for (int row = 0; row < layout.rowHeights().size(); row++) {
            int row_bottom = row_top + layout.rowHeights().get(row);
            if (content_y >= row_top && content_y < row_bottom) {
                int content_x = mouse_x - table_x;
                int column = 0;
                int column_left = 0;
                for (; column < layout.columnWidths().size() - 1; column++) {
                    column_left += layout.columnWidths().get(column);
                    if (content_x < column_left) {
                        break;
                    }
                }
                return new int[]{
                        row,
                        Math.min(this.columns - 1, Math.max(0, column))
                };
            }
            row_top = row_bottom;
        }
        return null;
    }

    // 行列结构变化后限制选区坐标，避免选区绘制访问已删除的行列
    private void clampSelection() {
        if (this.selection_start_row < 0 || this.selection_end_row < 0) {
            return;
        }
        int old_start_row = this.selection_start_row;
        int old_start_column = this.selection_start_column;
        int old_end_row = this.selection_end_row;
        int old_end_column = this.selection_end_column;
        int last_row = this.rows.size() - 1;
        int last_column = this.columns - 1;
        this.selection_start_row = Mth.clamp(this.selection_start_row, 0, last_row);
        this.selection_end_row = Mth.clamp(this.selection_end_row, 0, last_row);
        this.selection_start_column = Mth.clamp(this.selection_start_column, 0, last_column);
        this.selection_end_column = Mth.clamp(this.selection_end_column, 0, last_column);
        if (old_start_row != this.selection_start_row
                || old_start_column != this.selection_start_column
                || old_end_row != this.selection_end_row
                || old_end_column != this.selection_end_column) {
            QuestEnhance.LOGGER.debug(
                    "Clamped table selection: from ({}, {})-({}, {}) to ({}, {})-({}, {})",
                    old_start_row,
                    old_start_column,
                    old_end_row,
                    old_end_column,
                    this.selection_start_row,
                    this.selection_start_column,
                    this.selection_end_row,
                    this.selection_end_column
            );
        }
    }

    // 设置当前选区，并让选区左上角单元格保持可编辑状态
    private void setSelection(int start_row, int start_column, int end_row, int end_column) {
        this.setSelection(start_row, start_column, end_row, end_column, true);
    }

    // 更新拖拽选区时保留当前编辑单元格和输入框焦点
    private void setSelection(
            int start_row,
            int start_column,
            int end_row,
            int end_column,
            boolean begin_edit
    ) {
        if (this.rows.isEmpty() || this.columns < 1) {
            return;
        }
        start_row = Mth.clamp(start_row, 0, this.rows.size() - 1);
        start_column = Mth.clamp(start_column, 0, this.columns - 1);
        end_row = Mth.clamp(end_row, 0, this.rows.size() - 1);
        end_column = Mth.clamp(end_column, 0, this.columns - 1);
        QuestDescriptionTable.CellMerge start_merge = QuestDescriptionTable.mergeAt(
                this.tableData(),
                start_row,
                start_column
        );
        if (start_merge != null) {
            start_row = start_merge.row();
            start_column = start_merge.column();
        }
        QuestDescriptionTable.CellMerge end_merge = QuestDescriptionTable.mergeAt(
                this.tableData(),
                end_row,
                end_column
        );
        if (end_merge != null) {
            end_row = end_merge.row() + end_merge.rowSpan() - 1;
            end_column = end_merge.column() + end_merge.columnSpan() - 1;
        }
        this.selection_start_row = start_row;
        this.selection_start_column = start_column;
        this.selection_end_row = end_row;
        this.selection_end_column = end_column;
        if (begin_edit) {
            int editing_row = Math.min(start_row, end_row);
            int editing_column = Math.min(start_column, end_column);
            QuestDescriptionTable.CellMerge editing_merge = QuestDescriptionTable.mergeAt(
                    this.tableData(),
                    editing_row,
                    editing_column
            );
            this.beginCellEdit(
                    editing_merge == null ? editing_row : editing_merge.row(),
                    editing_merge == null ? editing_column : editing_merge.column()
            );
        }
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
        this.loading_cell_editor = true;
        this.direct_cell_editor.setText(value);
        this.direct_cell_editor.setCursorPos(value.length());
        this.direct_cell_editor.setSelectionPos(0);
        this.loading_cell_editor = false;
        this.direct_cell_editor_visible = true;
        this.direct_cell_editor.setFocused(false);
    }

    // 结束当前单元格编辑并隐藏输入框
    private void finishCellEdit() {
        if (this.cell_editor != null) {
            this.cell_editor.setFocused(false);
        }
        if (this.direct_cell_editor != null) {
            this.direct_cell_editor.setFocused(false);
        }
        this.direct_cell_editor_visible = false;
        this.editing_row = -1;
        this.editing_column = -1;
        this.loading_cell_editor = false;
        this.cell_edit_changed = false;
    }

    // 同步顶部输入框和单元格内输入框，并为本次编辑建立一个撤销快照
    private void onCellEditorChanged(TextBox source) {
        if (this.loading_cell_editor
                || this.editing_row < 0
                || this.editing_row >= this.rows.size()
                || this.editing_column < 0
                || this.editing_column >= this.columns) {
            return;
        }
        if (!this.cell_edit_changed) {
            this.pushUndo(this.tableData());
            this.cell_edit_changed = true;
        }
        String value = source.getText();
        this.rows.get(this.editing_row).set(this.editing_column, value);
        TextBox target = source == this.cell_editor ? this.direct_cell_editor : this.cell_editor;
        this.loading_cell_editor = true;
        target.setText(value);
        this.loading_cell_editor = false;
    }

    // 根据滚动位置更新单元格内输入框的可见区域
    private void updateDirectCellEditorPosition(QuestDescriptionTable.TableLayout layout) {
        if (this.editing_row < 0 || this.editing_column < 0) {
            this.direct_cell_editor_visible = false;
            return;
        }
        QuestDescriptionTable.CellRegion region = QuestDescriptionTable.regionFor(
                layout,
                this.tableData(),
                this.editing_row,
                this.editing_column
        );
        int left = 8 + region.x() + 1;
        int top = TABLE_TOP + region.y() - this.table_scroll + 1;
        int right = 8 + region.x() + region.width() - 1;
        int bottom = TABLE_TOP + region.y() + region.height() - this.table_scroll - 1;
        int visible_top = Math.max(TABLE_TOP + 1, top);
        int visible_bottom = Math.min(this.height - FOOTER_HEIGHT - 1, bottom);
        this.direct_cell_editor_visible = visible_bottom > visible_top && right > left;
        if (this.direct_cell_editor_visible) {
            this.direct_cell_editor.setPosAndSize(left, visible_top, right - left, visible_bottom - visible_top);
        }
    }

    // 为当前选区绘制明显的绿色边框，覆盖单元格默认边框
    private void drawSelection(
            GuiGraphics graphics,
            QuestDescriptionTable.TableLayout layout,
            int table_x,
            int table_y
    ) {
        if (this.rows.isEmpty() || this.columns < 1
                || this.selection_start_row < 0 || this.selection_end_row < 0) {
            return;
        }
        this.clampSelection();
        int top_row = Math.min(this.selection_start_row, this.selection_end_row);
        int bottom_row = Math.max(this.selection_start_row, this.selection_end_row);
        int left_column = Math.min(this.selection_start_column, this.selection_end_column);
        int right_column = Math.max(this.selection_start_column, this.selection_end_column);
        QuestDescriptionTable.CellRegion region = QuestDescriptionTable.regionFor(
                layout,
                this.columns,
                top_row,
                left_column,
                bottom_row - top_row + 1,
                right_column - left_column + 1
        );
        int border_width = 2;
        Color4I selection_color = Color4I.rgb(0x20D6A1);
        int left = table_x + region.x();
        int top = table_y + region.y();
        selection_color.draw(graphics, left, top, region.width(), border_width);
        selection_color.draw(graphics, left, top + region.height() - border_width, region.width(), border_width);
        selection_color.draw(graphics, left, top, border_width, region.height());
        selection_color.draw(graphics, left + region.width() - border_width, top, border_width, region.height());
    }

    // 合并当前矩形选区并保留左上角单元格的内容
    private void mergeSelection() {
        this.finishCellEdit();
        int top_row = Math.min(this.selection_start_row, this.selection_end_row);
        int bottom_row = Math.max(this.selection_start_row, this.selection_end_row);
        int left_column = Math.min(this.selection_start_column, this.selection_end_column);
        int right_column = Math.max(this.selection_start_column, this.selection_end_column);
        if (top_row < 0
                || left_column < 0
                || (top_row == bottom_row && left_column == right_column)) {
            return;
        }
        for (QuestDescriptionTable.CellMerge merge : this.merges) {
            boolean overlaps = merge.row() <= bottom_row
                    && merge.row() + merge.rowSpan() - 1 >= top_row
                    && merge.column() <= right_column
                    && merge.column() + merge.columnSpan() - 1 >= left_column;
            boolean contained = merge.row() >= top_row
                    && merge.row() + merge.rowSpan() - 1 <= bottom_row
                    && merge.column() >= left_column
                    && merge.column() + merge.columnSpan() - 1 <= right_column;
            if (overlaps && !contained) {
                return;
            }
        }
        QuestDescriptionTable.TableData previous = this.tableData();
        this.merges.removeIf(merge -> merge.row() >= top_row
                && merge.row() + merge.rowSpan() - 1 <= bottom_row
                && merge.column() >= left_column
                && merge.column() + merge.columnSpan() - 1 <= right_column);
        String value = this.rows.get(top_row).get(left_column);
        for (int row = top_row; row <= bottom_row; row++) {
            for (int column = left_column; column <= right_column; column++) {
                this.rows.get(row).set(column, row == top_row && column == left_column ? value : "");
            }
        }
        this.merges.add(new QuestDescriptionTable.CellMerge(
                top_row,
                left_column,
                bottom_row - top_row + 1,
                right_column - left_column + 1
        ));
        this.pushUndo(previous);
        this.setSelection(top_row, left_column, bottom_row, right_column);
    }

    // 拆分包含当前选区左上角的合并区域
    private void splitSelection() {
        this.finishCellEdit();
        int row = Math.min(this.selection_start_row, this.selection_end_row);
        int column = Math.min(this.selection_start_column, this.selection_end_column);
        QuestDescriptionTable.CellMerge target = QuestDescriptionTable.mergeAt(this.tableData(), row, column);
        if (target == null) {
            return;
        }
        QuestDescriptionTable.TableData previous = this.tableData();
        this.merges.remove(target);
        this.pushUndo(previous);
        this.setSelection(
                target.row(),
                target.column(),
                target.row() + target.rowSpan() - 1,
                target.column() + target.columnSpan() - 1
        );
    }

    // 打开只作用于右键单元格的独立设置页。
    private void openCellConfig(int context_row, int context_column) {
        QuestDescriptionTable.TableData data = this.tableData();
        QuestDescriptionTable.CellMerge target_merge = QuestDescriptionTable.mergeAt(
                data,
                context_row,
                context_column
        );
        int target_row = target_merge == null ? context_row : target_merge.row();
        int target_column = target_merge == null ? context_column : target_merge.column();
        QuestDescriptionTable.CellStyle current = data.cellStyle(target_row, target_column);
        boolean[] next_header = {current.header()};
        QuestDescriptionTable.TextAlignment[] next_alignment = {current.alignment()};
        int[] next_cell_width = {current.cellWidth()};
        int[] next_row_height = {current.rowHeight()};
        int[] next_line_width = {current.lineWidth()};
        Color4I[] next_border_color = {current.borderColor()};
        Color4I[] next_header_color = {current.headerColor()};
        Color4I[] next_cell_color = {current.cellColor()};
        Color4I[] next_text_color = {current.textColor()};

        // 确认后把样式写入当前单元格，合并区域统一使用左上角样式。
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                QuestDescriptionTable.TableData previous = this.tableData();
                QuestDescriptionTable.CellStyle next_style = new QuestDescriptionTable.CellStyle(
                        next_header[0],
                        next_alignment[0],
                        next_cell_width[0],
                        next_row_height[0],
                        next_line_width[0],
                        next_border_color[0],
                        next_header_color[0],
                        next_cell_color[0],
                        next_text_color[0]
                );
                int start_row = target_merge == null ? target_row : target_merge.row();
                int start_column = target_merge == null ? target_column : target_merge.column();
                int end_row = target_merge == null ? target_row + 1 : target_merge.row() + target_merge.rowSpan();
                int end_column = target_merge == null
                        ? target_column + 1
                        : target_merge.column() + target_merge.columnSpan();
                for (int row = start_row; row < end_row; row++) {
                    for (int column = start_column; column < end_column; column++) {
                        this.cell_styles.set(row * this.columns + column, next_style);
                    }
                }
                if (!previous.equals(this.tableData())) {
                    this.pushUndo(previous);
                }
            }
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.description_component.table.cell_settings");
            }
        };
        group.addInt("cell_width", next_cell_width[0], value -> next_cell_width[0] = value, 0, 0, 1000)
                .setNameKey("quest_enhance.description_component.table.cell_width");
        group.addBool("header", next_header[0], value -> next_header[0] = value, true)
                .setNameKey("quest_enhance.description_component.table.cell_header");
        group.addEnum(
                "alignment",
                next_alignment[0],
                value -> next_alignment[0] = value,
                QuestDescriptionTable.TextAlignment.NAME_MAP,
                QuestDescriptionTable.TextAlignment.LEFT
        ).setNameKey("quest_enhance.description_component.table.alignment");
        group.addInt("row_height", next_row_height[0], value -> next_row_height[0] = value, 18, 12, 30)
                .setNameKey("quest_enhance.description_component.table.cell_row_height");
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
        new EditConfigScreen(group).setAutoclose(true).openGui();
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
        this.merges.clear();
        this.merges.addAll(data.merges());
        this.cell_styles.clear();
        this.cell_styles.addAll(data.cellStyles());
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
        this.clampSelection();
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
                this.text_color,
                List.copyOf(this.merges),
                List.copyOf(this.cell_styles)
        );
    }
}
