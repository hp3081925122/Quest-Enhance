package com.quest_enhance.common.description;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.common.canvas.ChapterCanvasData;
import net.minecraft.core.HolderLookup;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class QuestDescriptionComponents {
        private static final String GIF_PROPERTY = "quest_enhance_gif";
    private static final String VIDEO_PROPERTY = "quest_enhance_video";
    private static final String VIDEO_LABEL_PROPERTY = "quest_enhance_video_label";
    private static final String TABLE_PROPERTY = "quest_enhance_table";
    private static final int TABLE_FORMAT_VERSION = 3;
    public static final String ITEM_ICON_PROPERTY = "quest_enhance_item";
    private static final Pattern WEB_URL = Pattern.compile("https?://[^\\s{}]+");
    private static final Pattern COMMAND = Pattern.compile("/.+");
    private static final Pattern TECHNICAL_KEY = Pattern.compile("[^\\s{}]+");
    private static final int MAX_COLUMNS = 8;
    private static final int MAX_ROWS = 16;
    private static final int MAX_CELL_LENGTH = 128;

    private QuestDescriptionComponents() {
    }

    // 生成带网页点击动作的原版 JSON 文字组件。
    public static String webLink(String displayText, String url) {
        requireMatch(url, WEB_URL, "url");
        return json(Component.literal(requireText(displayText, "displayText")).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))));
    }

    // 生成点击后复制内容的原版 JSON 文字组件。
    public static String copy(String displayText, String value) {
        return json(Component.literal(requireText(displayText, "displayText")).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(requireText(value, "value")))));
    }

    // 生成点击后执行命令的原版 JSON 文字组件。
    public static String command(String displayText, String command) {
        requireMatch(command, COMMAND, "command");
        return json(Component.literal(requireText(displayText, "displayText")).withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))));
    }

    // 生成带悬停文字的原版 JSON 文字组件。
    public static String hoverText(String displayText, String hoverText) {
        return json(Component.literal(requireText(displayText, "displayText")).withStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(requireText(hoverText, "hoverText"))))));
    }

    // 生成使用指定字体的原版 JSON 文字组件。
    public static String font(String displayText, String fontId) {
        Identifier font = Identifier.tryParse(requireText(fontId, "fontId"));
        if (font == null) {
            throw new IllegalArgumentException("fontId is not a valid resource location");
        }
        return json(Component.literal(requireText(displayText, "displayText"))
                .withStyle(style -> style.withFont(new FontDescription.Resource(font))));
    }

    // 生成带回退文字的本地化 JSON 文字组件。
    public static String translation(String displayText, String translationKey) {
        requireMatch(translationKey, TECHNICAL_KEY, "translationKey");
        return json(Component.translatableWithFallback(translationKey, requireText(displayText, "displayText")));
    }

    // 生成按键绑定 JSON 文字组件。
    public static String keybind(String keybind) {
        requireMatch(keybind, TECHNICAL_KEY, "keybind");
        return json(Component.keybind(keybind));
    }

    // 生成读取玩家持久化数据的描述占位组件。
    public static String playerPersistentData(String key, boolean i18n) {
        return PlayerPersistentDataDescription.create(key, i18n);
    }

    // 生成带完整物品 NBT 的悬停信息 JSON 组件。
    public static String itemHover(String displayText, ItemStack stack) {
        requireStack(stack);
        return json(Component.literal(requireText(displayText, "displayText")).withStyle(Style.EMPTY
                .withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack.copy())))));
    }

    // 生成 FTB Library 原生图片标记。
    public static String image(
            String icon,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        String normalizedAlignment = normalizeAlignment(alignment);
        String normalizedHoverText = hoverText == null ? "" : hoverText;
        if (normalizedHoverText.indexOf('{') >= 0 || normalizedHoverText.indexOf('}') >= 0) {
            throw new IllegalArgumentException("hoverText cannot contain braces");
        }

        return imageMarkup(
                "image",
                requireText(icon, "icon")
                        .replace("\\", "\\\\")
                        .replace("{", "\\{")
                        .replace("}", "\\}")
                        .replace(" ", "%20"),
                width,
                height,
                normalizedAlignment,
                fit,
                normalizedHoverText
        );
    }

    // 校验网址后生成描述中的网络图片标记。
    public static String remoteImage(
            String url,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        requireMatch(url, WEB_URL, "url");
        return image(url, width, height, alignment, fit, hoverText);
    }

    // 按 FTB ItemIcon 的保存规则生成包含数量、损伤值和 NBT 的物品图片标记。
    public static String itemIcon(
            ItemStack stack,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        requireStack(stack);
        String encodedStack = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(encodeItemStack(stack).getBytes(StandardCharsets.UTF_8));
        String normalizedHoverText = hoverText == null ? "" : hoverText;
        if (normalizedHoverText.indexOf('{') >= 0 || normalizedHoverText.indexOf('}') >= 0) {
            throw new IllegalArgumentException("hoverText cannot contain braces");
        }
        String markup = imageMarkup(
                ITEM_ICON_PROPERTY,
                encodedStack,
                width,
                height,
                normalizeAlignment(alignment),
                fit,
                normalizedHoverText
        );
        return markup;
    }

    // 生成由客户端按真实宽高比补齐布局的简洁 GIF 标记。
    public static String gif(String resourceId) {
        return "{" + GIF_PROPERTY + ":" + requireGifResource(resourceId) + "}";
    }

    // 生成任务描述 GIF 标记并保存布局参数。
    public static String gif(
            String resourceId,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        Identifier resource = requireGifResource(resourceId);
        String normalizedHoverText = hoverText == null ? "" : hoverText;
        if (normalizedHoverText.indexOf('{') >= 0 || normalizedHoverText.indexOf('}') >= 0) {
            throw new IllegalArgumentException("hoverText cannot contain braces");
        }
        StringBuilder markup = new StringBuilder("{")
                .append(GIF_PROPERTY).append(':').append(resource)
                .append(" width:").append(clamp(width, 1, 1000))
                .append(" height:").append(clamp(height, 1, 1000))
                .append(" align:").append(normalizeAlignment(alignment));
        if (fit) {
            markup.append(" fit:true");
        }
        if (!normalizedHoverText.isBlank()) {
            markup.append(" text:").append(normalizedHoverText.replace(" ", "%20"));
        }
        return markup.append('}').toString();
    }

    // 生成使用 URL-safe Base64 保存路径的视频标记。
    public static String video(String videoPath, String displayText) {
        String normalizedPath = ChapterCanvasData.normalizeVideoPath(videoPath)
                .orElseThrow(() -> new IllegalArgumentException("videoPath is not a valid relative video path"));
        String encodedPath = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(normalizedPath.getBytes(StandardCharsets.UTF_8));
        String escapedText = requireText(displayText, "displayText")
                .replace("%", "%25")
                .replace("{", "%7B")
                .replace("}", "%7D")
                .replace(" ", "%20");
        return "{" + VIDEO_PROPERTY + ":" + encodedPath + " "
                + VIDEO_LABEL_PROPERTY + ":" + escapedText + "}";
    }

    // 根据行数据自动推导列数并生成表格标记。
    public static String table(
            List<?> rows,
            boolean header,
            String alignment,
            int tableWidth,
            int rowHeight,
            int lineWidth,
            int borderColor,
            int headerColor,
            int cellColor,
            int textColor
    ) {
        if (rows == null || rows.isEmpty() || rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("rows must contain between 1 and 16 rows");
        }
        int columns = 0;
        for (Object rowValue : rows) {
            if (!(rowValue instanceof List<?> row)) {
                throw new IllegalArgumentException("each table row must be an array");
            }
            columns = Math.max(columns, row.size());
        }
        return table(
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
                textColor
        );
    }

    // 使用明确列数生成与客户端表格编辑器完全一致的 Base64 数据。
    public static String table(
            int columns,
            List<?> rows,
            boolean header,
            String alignment,
            int tableWidth,
            int rowHeight,
            int lineWidth,
            int borderColor,
            int headerColor,
            int cellColor,
            int textColor
    ) {
        return table(
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
                List.of(),
                List.of()
        );
    }

    // 保留仅带合并信息的旧调用方式。
    public static String table(
            int columns,
            List<?> rows,
            boolean header,
            String alignment,
            int tableWidth,
            int rowHeight,
            int lineWidth,
            int borderColor,
            int headerColor,
            int cellColor,
            int textColor,
            List<TableMerge> merges
    ) {
        return table(
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

    // 使用明确列数、合并信息和单元格样式生成表格标记。
    public static String table(
            int columns,
            List<?> rows,
            boolean header,
            String alignment,
            int tableWidth,
            int rowHeight,
            int lineWidth,
            int borderColor,
            int headerColor,
            int cellColor,
            int textColor,
            List<TableMerge> merges,
            List<TableCellStyle> cellStyles
    ) {
        if (columns < 1 || columns > MAX_COLUMNS) {
            throw new IllegalArgumentException("columns must be between 1 and 8");
        }
        if (rows == null || rows.isEmpty() || rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("rows must contain between 1 and 16 rows");
        }

        List<List<String>> normalizedRows = new ArrayList<>(rows.size());
        for (Object rowValue : rows) {
            if (!(rowValue instanceof List<?> row)) {
                throw new IllegalArgumentException("each table row must be an array");
            }
            List<String> normalizedRow = new ArrayList<>(columns);
            for (int column = 0; column < columns; column++) {
                String value = column < row.size() && row.get(column) != null
                        ? String.valueOf(row.get(column))
                        : "";
                normalizedRow.add(value.length() > MAX_CELL_LENGTH
                        ? value.substring(0, MAX_CELL_LENGTH)
                        : value);
            }
            normalizedRows.add(List.copyOf(normalizedRow));
        }

        String normalizedAlignment = normalizeAlignment(alignment);
        JsonObject json = new JsonObject();
        json.addProperty("format_version", TABLE_FORMAT_VERSION);
        json.addProperty("columns", columns);
        json.addProperty("header", header);
        json.addProperty("alignment", normalizedAlignment);
        json.addProperty("table_width", clamp(tableWidth, 0, 1000));
        json.addProperty("row_height", clamp(rowHeight, 12, 30));
        json.addProperty("line_width", clamp(lineWidth, 1, 4));
        json.addProperty("border_color", borderColor);
        json.addProperty("header_color", headerColor);
        json.addProperty("cell_color", cellColor);
        json.addProperty("text_color", textColor);
        JsonArray jsonRows = new JsonArray();
        for (List<String> row : normalizedRows) {
            JsonArray cells = new JsonArray();
            row.forEach(cells::add);
            jsonRows.add(cells);
        }
        json.add("rows", jsonRows);
        JsonArray jsonMerges = new JsonArray();
        if (merges != null) {
            for (TableMerge merge : merges) {
                if (merge != null
                        && merge.row() >= 0
                        && merge.column() >= 0
                        && (merge.rowSpan() > 1 || merge.columnSpan() > 1)
                        && merge.row() + merge.rowSpan() <= normalizedRows.size()
                        && merge.column() + merge.columnSpan() <= columns) {
                    JsonObject jsonMerge = new JsonObject();
                    jsonMerge.addProperty("row", merge.row());
                    jsonMerge.addProperty("column", merge.column());
                    jsonMerge.addProperty("row_span", merge.rowSpan());
                    jsonMerge.addProperty("column_span", merge.columnSpan());
                    jsonMerges.add(jsonMerge);
                }
            }
        }
        json.add("merges", jsonMerges);
        JsonArray jsonCellStyles = new JsonArray();
        int normalizedRowHeight = clamp(rowHeight, 12, 30);
        int normalizedLineWidth = clamp(lineWidth, 1, 4);
        String normalizedTableAlignment = normalizedAlignment;
        if (cellStyles != null) {
            for (int index = 0; index < normalizedRows.size() * columns && index < cellStyles.size(); index++) {
                TableCellStyle cellStyle = cellStyles.get(index);
                if (cellStyle == null) {
                    continue;
                }
                boolean defaultHeader = header && index / columns == 0;
                if (cellStyle.header() == defaultHeader
                        && normalizeAlignment(cellStyle.alignment()).equals(normalizedTableAlignment)
                        && clamp(cellStyle.cellWidth(), 0, 1000) == 0
                        && clamp(cellStyle.rowHeight(), 12, 30) == normalizedRowHeight
                        && clamp(cellStyle.lineWidth(), 1, 4) == normalizedLineWidth
                        && cellStyle.borderColor() == borderColor
                        && cellStyle.headerColor() == headerColor
                        && cellStyle.cellColor() == cellColor
                        && cellStyle.textColor() == textColor) {
                    continue;
                }
                JsonObject jsonCellStyle = new JsonObject();
                jsonCellStyle.addProperty("row", index / columns);
                jsonCellStyle.addProperty("column", index % columns);
                jsonCellStyle.addProperty("header", cellStyle.header());
                jsonCellStyle.addProperty("alignment", normalizeAlignment(cellStyle.alignment()));
                jsonCellStyle.addProperty("cell_width", clamp(cellStyle.cellWidth(), 0, 1000));
                jsonCellStyle.addProperty("row_height", clamp(cellStyle.rowHeight(), 12, 30));
                jsonCellStyle.addProperty("line_width", clamp(cellStyle.lineWidth(), 1, 4));
                jsonCellStyle.addProperty("border_color", cellStyle.borderColor());
                jsonCellStyle.addProperty("header_color", cellStyle.headerColor());
                jsonCellStyle.addProperty("cell_color", cellStyle.cellColor());
                jsonCellStyle.addProperty("text_color", cellStyle.textColor());
                jsonCellStyles.add(jsonCellStyle);
            }
        }
        json.add("cell_styles", jsonCellStyles);
        String encoded = compressTableJson(json.toString());
        return "{" + TABLE_PROPERTY + ":" + encoded + "}";
    }

    // 表格单元格合并区域的序列化数据。
    public record TableMerge(int row, int column, int rowSpan, int columnSpan) {
    }

    // 表格单元格的独立显示样式。
    public record TableCellStyle(
            boolean header,
            String alignment,
            int cellWidth,
            int rowHeight,
            int lineWidth,
            int borderColor,
            int headerColor,
            int cellColor,
            int textColor
    ) {
    }

    // 使用原版序列化器统一处理 JSON 转义。
    private static String json(Component component) {
        HolderLookup.Provider provider = clientProvider();
        var ops = provider == null
                ? com.mojang.serialization.JsonOps.INSTANCE
                : provider.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        return ComponentSerialization.CODEC.encodeStart(ops, component)
                .result()
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Failed to serialize component"));
    }

    // 使用当前任务书注册表编码完整物品数据，保留数量和数据组件。
    private static String encodeItemStack(ItemStack stack) {
        HolderLookup.Provider provider = clientProvider();
        var ops = provider == null
                ? com.mojang.serialization.JsonOps.INSTANCE
                : provider.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, stack)
                .result()
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("Failed to serialize item stack"));
    }

    // 在服务端和客户端分别取得当前任务书的注册表访问器
    private static HolderLookup.Provider clientProvider() {
        ServerQuestFile file = ServerQuestFile.getInstance();
        if (file != null) {
            return file.holderLookup();
        }

        try {
            Class<?> minecraft_class = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraft_class.getMethod("getInstance").invoke(null);
            Object level = minecraft_class.getField("level").get(minecraft);
            return level == null
                    ? null
                    : (HolderLookup.Provider) level.getClass().getMethod("registryAccess").invoke(level);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    // 生成图片类描述组件共用的尺寸、对齐、自适应和悬停属性。
    private static String imageMarkup(
            String property,
            String value,
            int width,
            int height,
            String alignment,
            boolean fit,
            String hoverText
    ) {
        StringBuilder markup = new StringBuilder("{")
                .append(property).append(':').append(value)
                .append(" width:").append(clamp(width, 1, 1000))
                .append(" height:").append(clamp(height, 1, 1000))
                .append(" align:").append(alignment);
        if (fit) {
            markup.append(" fit:true");
        }
        if (!hoverText.isBlank()) {
            markup.append(" text:").append(hoverText.replace(" ", "%20"));
        }
        return markup.append('}').toString();
    }

    // 压缩表格 JSON，减少描述编辑器中单个表格标记占用的行数。
    private static String compressTableJson(String json) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(
                    output,
                    new Deflater(Deflater.BEST_COMPRESSION)
            )) {
                deflater.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compress table markup", exception);
        }
    }

    // 校验所有不可为空的描述参数。
    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }

    // 按描述编辑器现有规则校验网址、命令和技术键。
    private static void requireMatch(String value, Pattern pattern, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " has an invalid format");
        }
    }

    // 拒绝无法显示或序列化的空物品。
    private static void requireStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("stack cannot be empty");
        }
    }

    // 限制 GIF 使用可随模组资源分发的有效路径。
    private static Identifier requireGifResource(String resourceId) {
        Identifier resource = Identifier.tryParse(requireText(resourceId, "resourceId"));
        if (resource == null
                || !resource.getNamespace().equals(QuestEnhance.MOD_ID)
                || !resource.getPath().startsWith("textures/")
                || !resource.getPath().endsWith(".gif")) {
            throw new IllegalArgumentException("resourceId is not a valid Quest Enhance GIF resource");
        }
        return resource;
    }

    // 图片和表格共用左、中、右三种对齐值。
    private static String normalizeAlignment(String alignment) {
        String normalized = requireText(alignment, "alignment").toLowerCase(Locale.ROOT);
        if (!normalized.equals("left") && !normalized.equals("center") && !normalized.equals("right")) {
            throw new IllegalArgumentException("alignment must be left, center or right");
        }
        return normalized;
    }

    // 将脚本输入限制到客户端渲染器支持的范围。
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
