package com.quest_enhance.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class QuestDescriptionTable {
    public static final String CLICK_PREFIX = "quest_enhance_table/";
    private static final String PROPERTY = "quest_enhance_table";
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
                Color4I.WHITE
        );
    }

    // 打开新建和编辑共用的可视化表格编辑器
    public static void openConfig(Panel parent, TableData initial, Consumer<String> save) {
        TableEditorScreen.open(parent, initial, save);
    }

    // 将表格数据编码为不会受空格和中文影响的单行描述标记
    public static String createMarkup(TableData data) {
        JsonObject json = new JsonObject();
        json.addProperty("columns", data.columns());
        json.addProperty("header", data.header());
        json.addProperty("alignment", data.alignment().serializedName());
        json.addProperty("centered", data.alignment() == TextAlignment.CENTER);
        json.addProperty("table_width", data.tableWidth());
        json.addProperty("row_height", data.rowHeight());
        json.addProperty("line_width", data.lineWidth());
        json.addProperty("border_color", data.borderColor().rgba());
        json.addProperty("header_color", data.headerColor().rgba());
        json.addProperty("cell_color", data.cellColor().rgba());
        json.addProperty("text_color", data.textColor().rgba());
        JsonArray rows = new JsonArray();
        for (List<String> row : data.rows()) {
            JsonArray cells = new JsonArray();
            row.forEach(cells::add);
            rows.add(cells);
        }
        json.add("rows", rows);
        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
        return "{" + PROPERTY + ":" + encoded + "}";
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
        if (component == null || component.getStyle().getClickEvent() == null) {
            return Optional.empty();
        }
        ClickEvent click_event = component.getStyle().getClickEvent();
        if (click_event.getAction() != ClickEvent.Action.CHANGE_PAGE
                || !click_event.getValue().startsWith(CLICK_PREFIX)) {
            return Optional.empty();
        }
        return decode(click_event.getValue().substring(CLICK_PREFIX.length()));
    }

    // 根据表格宽度计算每一行自动换行后的实际高度
    public static TableLayout layout(Theme theme, int available_width, TableData data) {
        int table_width = data.tableWidth() <= 0
                ? Math.max(1, available_width)
                : Math.max(1, Math.min(available_width, data.tableWidth()));
        List<Integer> row_heights = new ArrayList<>(data.rows().size());
        int total_height = data.lineWidth();

        // 每行使用换行最多的单元格决定高度
        for (List<String> row : data.rows()) {
            int wrapped_lines = 1;
            for (int column = 0; column < data.columns(); column++) {
                int left = column * table_width / data.columns();
                int right = (column + 1) * table_width / data.columns();
                List<FormattedText> lines = theme.listFormattedStringToWidth(
                        Component.literal(row.get(column)),
                        Math.max(1, right - left - 6 - data.lineWidth())
                );
                wrapped_lines = Math.max(wrapped_lines, Math.max(1, lines.size()));
            }
            int row_height = Math.max(
                    data.rowHeight(),
                    wrapped_lines * theme.getFontHeight() + 6
            );
            row_heights.add(row_height);
            total_height += row_height;
        }
        return new TableLayout(table_width, List.copyOf(row_heights), total_height);
    }

    // 根据表格数据绘制背景、网格线和自动换行后的单元格文字
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
        int row_y = y;
        for (int row = 0; row < data.rows().size(); row++) {
            int row_height = layout.rowHeights().get(row);
            Color4I background = data.header() && row == 0 ? data.headerColor() : data.cellColor();
            background.draw(graphics, x, row_y, table_width, row_height);
            for (int column = 0; column < data.columns(); column++) {
                int left = x + column * table_width / data.columns();
                int right = x + (column + 1) * table_width / data.columns();
                String value = data.rows().get(row).get(column);
                List<FormattedText> lines = theme.listFormattedStringToWidth(
                        Component.literal(value),
                        Math.max(1, right - left - 6 - data.lineWidth())
                );
                int text_y = row_y + Math.max(
                        2,
                        (row_height - Math.max(1, lines.size()) * theme.getFontHeight()) / 2
                );

                // 每一段换行文字分别按当前对齐方式绘制
                for (FormattedText line : lines) {
                    int text_x = switch (data.alignment()) {
                        case LEFT -> left + 3 + data.lineWidth();
                        case CENTER -> left + Math.max(
                                0,
                                (right - left - theme.getStringWidth(line)) / 2
                        );
                        case RIGHT -> right - 3 - data.lineWidth() - theme.getStringWidth(line);
                    };
                    theme.drawString(graphics, line, text_x, text_y, data.textColor(), 0);
                    text_y += theme.getFontHeight();
                }
            }
            row_y += row_height;
        }

        // 最后绘制外框和内部横竖分隔线
        int height = layout.height();
        int line_width = data.lineWidth();
        data.borderColor().draw(graphics, x, y, table_width, line_width);
        data.borderColor().draw(graphics, x, y + height - line_width, table_width, line_width);
        data.borderColor().draw(graphics, x, y, line_width, height);
        data.borderColor().draw(graphics, x + table_width - line_width, y, line_width, height);
        int line_y = y;
        for (int row = 1; row < data.rows().size(); row++) {
            line_y += layout.rowHeights().get(row - 1);
            data.borderColor().draw(graphics, x, line_y, table_width, line_width);
        }
        for (int column = 1; column < data.columns(); column++) {
            int line_x = x + column * table_width / data.columns();
            data.borderColor().draw(graphics, line_x, y, line_width, height);
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
            String json_text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(json_text).getAsJsonObject();
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
            int row_height = Math.max(12, Math.min(30, json.get("row_height").getAsInt()));
            return Optional.of(new TableData(
                    columns,
                    rows,
                    json.get("header").getAsBoolean(),
                    json.has("alignment")
                            ? TextAlignment.fromSerialized(json.get("alignment").getAsString())
                            : json.has("centered") && json.get("centered").getAsBoolean()
                            ? TextAlignment.CENTER
                            : TextAlignment.LEFT,
                    json.has("table_width")
                            ? Math.max(0, Math.min(1000, json.get("table_width").getAsInt()))
                            : 0,
                    row_height,
                    json.has("line_width")
                            ? Math.max(1, Math.min(4, json.get("line_width").getAsInt()))
                            : 1,
                    Color4I.rgba(json.get("border_color").getAsInt()),
                    Color4I.rgba(json.get("header_color").getAsInt()),
                    Color4I.rgba(json.get("cell_color").getAsInt()),
                    Color4I.rgba(json.get("text_color").getAsInt())
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
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
            Color4I textColor
    ) {
    }

    public record TableLayout(int width, List<Integer> rowHeights, int height) {
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
