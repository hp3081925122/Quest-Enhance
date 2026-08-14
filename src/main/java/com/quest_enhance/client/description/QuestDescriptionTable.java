package com.quest_enhance.client.description;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.InflaterInputStream;

public final class QuestDescriptionTable {
    public static final String CLICK_PREFIX = "quest_enhance_table/";
    private static final String PROPERTY = "quest_enhance_table";
    private static final int TABLE_FORMAT_VERSION = 3;
    static final int MAX_COLUMNS = 8;
    static final int MAX_ROWS = 16;
    static final int MAX_CELL_LENGTH = 128;

    private QuestDescriptionTable() {
    }

    // 注册任务描述表格标记解析器
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientTextComponentUtils.addCustomParser(QuestDescriptionTable::parse));
    }

    // 创建包含表头和两行示例内容的默认表格
    public static TableData defaultTable() {
        return new TableData(
                3,
                List.of(
                        List.of("标题 1", "标题 2", "标题 3"),
                        List.of("内容 1", "内容 2", "内容 3"),
                        List.of("内容 4", "内容 5", "内容 6")
                ),
                true,
                TextAlignment.LEFT,
                0,
                18,
                1,
                Color4I.GRAY,
                Color4I.rgb(0x374151),
                Color4I.rgb(0x1F2937),
                Color4I.WHITE,
                List.of()
        );
    }

    // 打开新建和编辑共用的可视化表格编辑器
    public static void openConfig(Panel parent, TableData initial, Consumer<String> save) {
        TableEditorScreen.open(parent, initial, save);
    }

    // 将表格数据编码为不会受空格和中文影响的单行描述标记
    public static String createMarkup(TableData data) {
        return QuestDescriptionComponents.table(
                data.columns(),
                data.rows(),
                data.header(),
                data.alignment().serializedName(),
                data.tableWidth(),
                data.rowHeight(),
                data.lineWidth(),
                data.borderColor().rgba(),
                data.headerColor().rgba(),
                data.cellColor().rgba(),
                data.textColor().rgba(),
                data.merges().stream()
                        .map(merge -> new QuestDescriptionComponents.TableMerge(
                                merge.row(),
                                merge.column(),
                                merge.rowSpan(),
                                merge.columnSpan()
                        ))
                        .toList(),
                data.cellStyles().stream()
                        .map(style -> new QuestDescriptionComponents.TableCellStyle(
                                style.header(),
                                style.alignment().serializedName(),
                                style.cellWidth(),
                                style.rowHeight(),
                                style.lineWidth(),
                                style.borderColor().rgba(),
                                style.headerColor().rgba(),
                                style.cellColor().rgba(),
                                style.textColor().rgba()
                        ))
                        .toList()
        );
    }

    // 从任务文件中的完整表格标记恢复配置数据
    public static Optional<TableData> decodeMarkup(String raw_text) {
        String prefix = "{" + PROPERTY + ":";
        if (!raw_text.startsWith(prefix) || !raw_text.endsWith("}")) {
            return Optional.empty();
        }
        return decode(raw_text.substring(prefix.length(), raw_text.length() - 1));
    }

    // 从已解析文字组件的内部点击值识别表格
    public static Optional<TableData> find(Component component) {
        if (component == null) {
            return Optional.empty();
        }
        ClickEvent click_event = component.getStyle().getClickEvent();
        if (click_event != null
                && click_event.getAction() == ClickEvent.Action.CHANGE_PAGE
                && click_event.getValue().startsWith(CLICK_PREFIX)) {
            return decode(click_event.getValue().substring(CLICK_PREFIX.length()));
        }

        // 1.20.1 会把自定义解析结果附加到根文本的子组件中
        for (Component sibling : component.getSiblings()) {
            Optional<TableData> nested = find(sibling);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    // 根据单元格宽度设置计算每一列的实际宽度。
    private static List<Integer> columnWidths(int table_width, TableData data) {
        int[] widths = new int[data.columns()];
        int auto_columns = 0;
        int requested_width = 0;
        for (int column = 0; column < data.columns(); column++) {
            int requested = 0;
            for (int row = 0; row < data.rows().size(); row++) {
                requested = Math.max(requested, data.cellStyle(row, column).cellWidth());
            }
            if (requested > 0) {
                widths[column] = requested;
                requested_width += requested;
            } else {
                auto_columns++;
            }
        }

        // 有自动列时，固定列优先占用宽度，剩余空间平均分给自动列。
        if (auto_columns > 0 && requested_width + auto_columns <= table_width) {
            int remaining = table_width - requested_width;
            int auto_width = remaining / auto_columns;
            int remainder = remaining % auto_columns;
            for (int column = 0; column < data.columns(); column++) {
                if (widths[column] == 0) {
                    widths[column] = auto_width + (remainder-- > 0 ? 1 : 0);
                }
            }
        } else if (auto_columns == 0 && requested_width <= table_width) {
            widths[data.columns() - 1] += table_width - requested_width;
        } else {
            // 固定列超出可用空间时按比例压缩，避免布局出现负宽度。
            int total_weight = 0;
            for (int column = 0; column < data.columns(); column++) {
                total_weight += widths[column] > 0 ? widths[column] : 1;
            }
            int assigned = 0;
            for (int column = 0; column < data.columns(); column++) {
                widths[column] = Math.max(1, table_width * (widths[column] > 0 ? widths[column] : 1) / total_weight);
                assigned += widths[column];
            }
            for (int column = data.columns() - 1; assigned < table_width; column = (column + data.columns() - 1) % data.columns()) {
                widths[column]++;
                assigned++;
            }
            for (int column = data.columns() - 1; assigned > table_width; column = (column + data.columns() - 1) % data.columns()) {
                if (widths[column] > 1) {
                    widths[column]--;
                    assigned--;
                }
            }
        }
        return java.util.Arrays.stream(widths).boxed().toList();
    }

    // 根据表格宽度计算普通单元格和合并区域的实际高度
    public static TableLayout layout(Theme theme, int available_width, TableData data) {
        int table_width = data.tableWidth() <= 0
                ? Math.max(1, available_width)
                : Math.max(1, Math.min(available_width, data.tableWidth()));
        List<Integer> column_widths = columnWidths(table_width, data);
        List<Integer> row_heights = new ArrayList<>(data.rows().size());
        for (int row = 0; row < data.rows().size(); row++) {
            int row_height = Math.max(12, data.rowHeight());
            for (int column = 0; column < data.columns(); column++) {
                row_height = Math.max(row_height, data.cellStyle(row, column).rowHeight());
            }
            row_heights.add(row_height);
        }

        // 每个文字区域按自身宽度计算换行，合并区域的高度由起始行承担额外空间
        for (int row = 0; row < data.rows().size(); row++) {
            for (int column = 0; column < data.columns(); column++) {
                CellMerge merge = mergeAt(data, row, column);
                if (merge != null && !merge.isAnchor(row, column)) {
                    continue;
                }
                int start_column = merge == null ? column : merge.column();
                int end_column = merge == null ? column + 1 : merge.column() + merge.columnSpan();
                int start_row = merge == null ? row : merge.row();
                int end_row = merge == null ? row + 1 : merge.row() + merge.rowSpan();
                int left = columnLeft(column_widths, start_column);
                int right = columnLeft(column_widths, end_column);
                CellStyle style = data.cellStyle(row, column);
                List<FormattedText> lines = theme.listFormattedStringToWidth(
                        Component.literal(data.rows().get(row).get(column)),
                        Math.max(1, right - left - 6 - style.lineWidth())
                );
                int required_height = Math.max(
                        style.rowHeight(),
                        Math.max(1, lines.size()) * theme.getFontHeight() + 6
                );
                int current_height = 0;
                for (int span_row = start_row; span_row < end_row; span_row++) {
                    current_height += row_heights.get(span_row);
                }
                if (required_height > current_height) {
                    row_heights.set(start_row, row_heights.get(start_row) + required_height - current_height);
                }
            }
        }
        int total_height = data.lineWidth();
        for (int row_height : row_heights) {
            total_height += row_height;
        }
        return new TableLayout(table_width, column_widths, List.copyOf(row_heights), total_height);
    }

    // 根据表格数据绘制背景、合并单元格、网格线和自动换行后的文字
    public static void draw(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            TableData data
    ) {
        TableLayout layout = layout(theme, width, data);
        int table_width = layout.width();
        for (int row = 0; row < data.rows().size(); row++) {
            for (int column = 0; column < data.columns(); column++) {
                CellMerge merge = mergeAt(data, row, column);
                if (merge != null && !merge.isAnchor(row, column)) {
                    continue;
                }
                CellRegion region = regionFor(layout, data, row, column);
                int left = x + region.x();
                int right = left + region.width();
                String value = data.rows().get(row).get(column);
                CellStyle style = data.cellStyle(row, column);
                List<FormattedText> lines = theme.listFormattedStringToWidth(
                        Component.literal(value),
                        Math.max(1, region.width() - 6 - style.lineWidth())
                );
                Color4I background = style.header() ? style.headerColor() : style.cellColor();
                background.draw(graphics, left, y + region.y(), region.width(), region.height());
                int text_y = y + region.y() + Math.max(
                        2,
                        (region.height() - Math.max(1, lines.size()) * theme.getFontHeight()) / 2
                );

                // 每一段换行文字分别按当前对齐方式绘制
                for (FormattedText line : lines) {
                    int text_x = switch (style.alignment()) {
                        case LEFT -> left + 3 + style.lineWidth();
                        case CENTER -> left + Math.max(
                                style.lineWidth(),
                                (region.width() - theme.getStringWidth(line)) / 2
                        );
                        case RIGHT -> right - 3 - style.lineWidth() - theme.getStringWidth(line);
                    };
                    theme.drawString(graphics, line, text_x, text_y, style.textColor(), 0);
                    text_y += theme.getFontHeight();
                }

                // 每个单元格独立绘制边框，合并区域只由左上角单元格绘制。
                int line_width = Math.max(1, Math.min(4, style.lineWidth()));
                style.borderColor().draw(graphics, left, y + region.y(), region.width(), line_width);
                style.borderColor().draw(
                        graphics,
                        left,
                        y + region.y() + region.height() - line_width,
                        region.width(),
                        line_width
                );
                style.borderColor().draw(graphics, left, y + region.y(), line_width, region.height());
                style.borderColor().draw(
                        graphics,
                        left + region.width() - line_width,
                        y + region.y(),
                        line_width,
                        region.height()
                );
            }
        }
    }

    // 把表格标记解析为带内部识别值的普通文字组件
    private static Component parse(String raw_text, Map<String, String> properties) {
        String encoded = properties.get(PROPERTY);
        if (encoded == null) {
            return null;
        }
        Optional<TableData> decoded = decode(encoded);
        if (decoded.isEmpty()) {
            return Component.translatable("quest_enhance.description_component.table.invalid");
        }
        TableData data = decoded.get();
        StringBuilder fallback = new StringBuilder();
        for (int row = 0; row < data.rows().size(); row++) {
            if (row > 0) {
                fallback.append('\n');
            }
            fallback.append(String.join(" | ", data.rows().get(row)));
        }
        return Component.literal(fallback.toString()).withStyle(Style.EMPTY.withClickEvent(new ClickEvent(
                ClickEvent.Action.CHANGE_PAGE,
                CLICK_PREFIX + encoded
        )));
    }

    // 解码并校验表格尺寸、颜色和单元格内容
    private static Optional<TableData> decode(String encoded) {
        try {
            String json_text = decodeTablePayload(encoded);
            JsonObject json = JsonParser.parseString(json_text).getAsJsonObject();
            int format_version = json.get("format_version").getAsInt();
            if (format_version != TABLE_FORMAT_VERSION) {
                return Optional.empty();
            }
            int columns = json.get("columns").getAsInt();
            if (columns < 1 || columns > MAX_COLUMNS) {
                return Optional.empty();
            }
            JsonArray json_rows = json.getAsJsonArray("rows");
            if (json_rows == null || json_rows.isEmpty() || json_rows.size() > MAX_ROWS) {
                return Optional.empty();
            }
            List<List<String>> rows = new ArrayList<>();
            for (JsonElement row_element : json_rows) {
                JsonArray json_cells = row_element.getAsJsonArray();
                List<String> row = new ArrayList<>(columns);
                for (int column = 0; column < columns; column++) {
                    String value = column < json_cells.size() ? json_cells.get(column).getAsString() : "";
                    row.add(value.length() > MAX_CELL_LENGTH
                            ? value.substring(0, MAX_CELL_LENGTH)
                            : value);
                }
                rows.add(List.copyOf(row));
            }
            List<CellMerge> merges = new ArrayList<>();
            JsonArray json_merges = json.getAsJsonArray("merges");
            if (json_merges != null) {
                for (JsonElement merge_element : json_merges) {
                    if (!merge_element.isJsonObject()) {
                        continue;
                    }
                    JsonObject merge = merge_element.getAsJsonObject();
                    int row = merge.get("row").getAsInt();
                    int column = merge.get("column").getAsInt();
                    int row_span = merge.get("row_span").getAsInt();
                    int column_span = merge.get("column_span").getAsInt();
                    CellMerge candidate = new CellMerge(row, column, row_span, column_span);
                    if (!candidate.isValid(rows.size(), columns)) {
                        continue;
                    }
                    boolean overlaps = false;
                    for (CellMerge existing : merges) {
                        if (existing.overlaps(candidate)) {
                            overlaps = true;
                            break;
                        }
                    }
                    if (!overlaps) {
                        merges.add(candidate);
                    }
                }
            }
            int row_height = Math.max(12, Math.min(30, json.get("row_height").getAsInt()));
            List<CellStyle> cell_styles = new ArrayList<>();
            JsonArray json_cell_styles = json.getAsJsonArray("cell_styles");
            if (format_version == TABLE_FORMAT_VERSION && json_cell_styles != null) {
                for (JsonElement style_element : json_cell_styles) {
                    if (!style_element.isJsonObject()) {
                        continue;
                    }
                    JsonObject style = style_element.getAsJsonObject();
                    int style_row = style.get("row").getAsInt();
                    int style_column = style.get("column").getAsInt();
                    if (style_row < 0
                            || style_row >= rows.size()
                            || style_column < 0
                            || style_column >= columns) {
                        continue;
                    }
                    while (cell_styles.size() < style_row * columns + style_column) {
                        cell_styles.add(null);
                    }
                    while (cell_styles.size() <= style_row * columns + style_column) {
                        cell_styles.add(null);
                    }
                    cell_styles.set(
                            style_row * columns + style_column,
                            new CellStyle(
                                    style.get("header").getAsBoolean(),
                                    TextAlignment.fromSerialized(style.get("alignment").getAsString()),
                                    Math.max(0, Math.min(1000, style.get("cell_width").getAsInt())),
                                    Math.max(12, Math.min(30, style.get("row_height").getAsInt())),
                                    Math.max(1, Math.min(4, style.get("line_width").getAsInt())),
                                    Color4I.rgba(style.get("border_color").getAsInt()),
                                    Color4I.rgba(style.get("header_color").getAsInt()),
                                    Color4I.rgba(style.get("cell_color").getAsInt()),
                                    Color4I.rgba(style.get("text_color").getAsInt())
                            )
                    );
                }
            }
            return Optional.of(new TableData(
                    columns,
                    rows,
                    json.get("header").getAsBoolean(),
                    TextAlignment.fromSerialized(json.get("alignment").getAsString()),
                    Math.max(0, Math.min(1000, json.get("table_width").getAsInt())),
                    row_height,
                    Math.max(1, Math.min(4, json.get("line_width").getAsInt())),
                    Color4I.rgba(json.get("border_color").getAsInt()),
                    Color4I.rgba(json.get("header_color").getAsInt()),
                    Color4I.rgba(json.get("cell_color").getAsInt()),
                    Color4I.rgba(json.get("text_color").getAsInt()),
                    List.copyOf(merges),
                    cell_styles
            ));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    // 读取当前版本的压缩表格标记。
    private static String decodeTablePayload(String encoded) throws IOException {
        byte[] payload = Base64.getUrlDecoder().decode(encoded);
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(payload));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            inflater.transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    // 查找包含指定单元格的合并区域。
    public static CellMerge mergeAt(TableData data, int row, int column) {
        for (CellMerge merge : data.merges()) {
            if (merge.contains(row, column)) {
                return merge;
            }
        }
        return null;
    }

    // 获取单元格或合并区域在表格中的像素边界。
    public static CellRegion regionFor(TableLayout layout, TableData data, int row, int column) {
        CellMerge merge = mergeAt(data, row, column);
        int start_row = merge == null ? row : merge.row();
        int start_column = merge == null ? column : merge.column();
        int end_row = merge == null ? row + 1 : merge.row() + merge.rowSpan();
        int end_column = merge == null ? column + 1 : merge.column() + merge.columnSpan();
        return regionFor(layout, data.columns(), start_row, start_column, end_row - start_row, end_column - start_column);
    }

    // 获取任意矩形选区在表格中的像素边界。
    public static CellRegion regionFor(
            TableLayout layout,
            int columns,
            int row,
            int column,
            int rowSpan,
            int columnSpan
    ) {
        int left = columnLeft(layout.columnWidths(), column);
        int right = columnLeft(layout.columnWidths(), column + columnSpan);
        return new CellRegion(
                left,
                rowTop(layout, row),
                right - left,
                rowTop(layout, row + rowSpan) - rowTop(layout, row)
        );
    }

    // 将列索引转换为表格内容中的横向像素位置。
    private static int columnLeft(List<Integer> column_widths, int column) {
        int left = 0;
        for (int index = 0; index < column; index++) {
            left += column_widths.get(index);
        }
        return left;
    }

    // 计算指定行在表格内容中的起始位置。
    private static int rowTop(TableLayout layout, int row) {
        int top = 0;
        for (int index = 0; index < row; index++) {
            top += layout.rowHeights().get(index);
        }
        return top;
    }

    public record TableData(
            int columns,
            List<List<String>> rows,
            boolean header,
            TextAlignment alignment,
            int tableWidth,
            int rowHeight,
            int lineWidth,
            Color4I borderColor,
            Color4I headerColor,
            Color4I cellColor,
            Color4I textColor,
            List<CellMerge> merges,
            List<CellStyle> cellStyles
    ) {
        // 旧版表格没有单元格样式时，把原整表设置复制到每个单元格作为兼容默认值
        public TableData(
                int columns,
                List<List<String>> rows,
                boolean header,
                TextAlignment alignment,
                int tableWidth,
                int rowHeight,
                int lineWidth,
                Color4I borderColor,
                Color4I headerColor,
                Color4I cellColor,
                Color4I textColor,
                List<CellMerge> merges
        ) {
            this(
                    columns,
                    rows,
                    header,
                    alignment,
                    tableWidth,
                    rowHeight,
                    lineWidth,
                    borderColor,
                    headerColor,
                    cellColor,
                    textColor,
                    merges,
                    List.of()
            );
        }

        public TableData {
            merges = merges == null ? List.of() : List.copyOf(merges);
            List<CellStyle> normalized_styles = new ArrayList<>(rows.size() * columns);
            for (int index = 0; index < rows.size() * columns; index++) {
                CellStyle style = cellStyles != null && index < cellStyles.size()
                        ? cellStyles.get(index)
                        : null;
                CellStyle default_style = new CellStyle(
                        header && index / columns == 0,
                        alignment,
                        0,
                        rowHeight,
                        lineWidth,
                        borderColor,
                        headerColor,
                        cellColor,
                        textColor
                );
                normalized_styles.add(style == null ? default_style : style);
            }
            cellStyles = List.copyOf(normalized_styles);
        }

        // 获取指定单元格的独立样式
        public CellStyle cellStyle(int row, int column) {
            return cellStyles.get(row * columns + column);
        }
    }

    public record CellStyle(
            boolean header,
            TextAlignment alignment,
            int cellWidth,
            int rowHeight,
            int lineWidth,
            Color4I borderColor,
            Color4I headerColor,
            Color4I cellColor,
            Color4I textColor
    ) {
    }

    public record CellMerge(int row, int column, int rowSpan, int columnSpan) {
        public boolean contains(int target_row, int target_column) {
            return target_row >= row
                    && target_row < row + rowSpan
                    && target_column >= column
                    && target_column < column + columnSpan;
        }

        public boolean isAnchor(int target_row, int target_column) {
            return row == target_row && column == target_column;
        }

        public boolean isValid(int rowCount, int columnCount) {
            return row >= 0
                    && column >= 0
                    && (rowSpan > 1 || columnSpan > 1)
                    && row + rowSpan <= rowCount
                    && column + columnSpan <= columnCount;
        }

        public boolean overlaps(CellMerge other) {
            return row < other.row() + other.rowSpan()
                    && row + rowSpan > other.row()
                    && column < other.column() + other.columnSpan()
                    && column + columnSpan > other.column();
        }
    }

    public record CellRegion(int x, int y, int width, int height) {
    }

    public record TableLayout(int width, List<Integer> columnWidths, List<Integer> rowHeights, int height) {
    }

    public enum TextAlignment {
        LEFT("left", "quest_enhance.description_component.table.alignment.left"),
        CENTER("center", "quest_enhance.description_component.table.alignment.center"),
        RIGHT("right", "quest_enhance.description_component.table.alignment.right");

        public static final NameMap<TextAlignment> NAME_MAP = NameMap.of(LEFT, values())
                .name(value -> Component.translatable(value.translation_key))
                .create();

        private final String serialized_name;
        private final String translation_key;

        TextAlignment(String serialized_name, String translation_key) {
            this.serialized_name = serialized_name;
            this.translation_key = translation_key;
        }

        // 使用稳定的小写名称保存配置，避免枚举重命名影响任务文件
        public String serializedName() {
            return this.serialized_name;
        }

        // 无效值回退到靠左，避免损坏整张表格
        public static TextAlignment fromSerialized(String value) {
            for (TextAlignment alignment : values()) {
                if (alignment.serialized_name.equals(value)) {
                    return alignment;
                }
            }
            return LEFT;
        }
    }
}
