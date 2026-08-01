package com.quest_enhance;

import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;
import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 管理章节中按任务与前置任务区分的隐藏前置线
public final class HiddenDependencyLines {
    private static final String NBT_KEY = "quest_enhance_hidden_dependency_lines";
    private static final String SOURCE_KEY = "source";
    private static final String DEPENDENCY_KEY = "dependency";
    private static final String REVEAL_ON_HOVER_KEY = "reveal_on_hover";
    private static final Map<Chapter, List<Line>> LINES = new WeakHashMap<>();

    private HiddenDependencyLines() {
    }

    // 获取章节内所有已隐藏的前置线
    public static List<Line> get(Chapter chapter) {
        return LINES.computeIfAbsent(chapter, ignored -> new ArrayList<>());
    }

    // 查找指定任务到前置任务的隐藏线设置
    public static Optional<Line> find(Chapter chapter, long source_id, long dependency_id) {
        return get(chapter).stream()
                .filter(line -> line.source_id() == source_id && line.dependency_id() == dependency_id)
                .findFirst();
    }

    // 更新一条前置线的隐藏和悬停显示设置
    public static void set(
            Chapter chapter,
            long source_id,
            long dependency_id,
            boolean hidden,
            boolean reveal_on_hover
    ) {
        List<Line> lines = get(chapter);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (line.source_id() != source_id || line.dependency_id() != dependency_id) {
                continue;
            }
            if (hidden) {
                lines.set(index, new Line(source_id, dependency_id, reveal_on_hover));
            } else {
                lines.remove(index);
            }
            return;
        }
        if (hidden) {
            lines.add(new Line(source_id, dependency_id, reveal_on_hover));
        }
    }

    // 删除任务时清理所有与之相连的隐藏前置线
    public static void removeQuest(Chapter chapter, long quest_id) {
        get(chapter).removeIf(line -> line.source_id() == quest_id || line.dependency_id() == quest_id);
    }

    // 将隐藏前置线写入章节存档数据
    public static void writeData(Chapter chapter, Json5Object tag) {
        Json5Array line_list = new Json5Array();
        for (Line line : get(chapter)) {
            Json5Object line_tag = new Json5Object();
            line_tag.addProperty(SOURCE_KEY, line.source_id());
            line_tag.addProperty(DEPENDENCY_KEY, line.dependency_id());
            line_tag.addProperty(REVEAL_ON_HOVER_KEY, line.reveal_on_hover());
            line_list.add(line_tag);
        }
        tag.add(NBT_KEY, line_list);
    }

    // 从章节存档数据恢复隐藏前置线
    public static void readData(Chapter chapter, Json5Object tag) {
        List<Line> lines = get(chapter);
        lines.clear();
        if (!tag.has(NBT_KEY)) {
            return;
        }
        Json5Element raw_line_list = tag.get(NBT_KEY);
        if (raw_line_list == null || !raw_line_list.isJson5Array()) {
            return;
        }
        Json5Array line_list = raw_line_list.getAsJson5Array();
        for (Json5Element raw_line : line_list) {
            if (!raw_line.isJson5Object()) {
                continue;
            }
            Json5Object line_tag = raw_line.getAsJson5Object();
            Json5Element raw_source = line_tag.get(SOURCE_KEY);
            Json5Element raw_dependency = line_tag.get(DEPENDENCY_KEY);
            if (raw_source == null || raw_dependency == null
                    || !raw_source.isJson5Primitive() || !raw_dependency.isJson5Primitive()) {
                continue;
            }
            Json5Primitive source = raw_source.getAsJson5Primitive();
            Json5Primitive dependency = raw_dependency.getAsJson5Primitive();
            if (!source.isNumber() || !dependency.isNumber()) {
                continue;
            }
            Json5Element raw_reveal = line_tag.get(REVEAL_ON_HOVER_KEY);
            boolean reveal_on_hover = raw_reveal != null
                    && raw_reveal.isJson5Primitive()
                    && raw_reveal.getAsJson5Primitive().isBoolean()
                    && raw_reveal.getAsBoolean();
            lines.add(new Line(
                    source.getAsLong(),
                    dependency.getAsLong(),
                    reveal_on_hover
            ));
        }
    }

    // 将隐藏前置线写入 FTB Quests 的章节网络数据
    public static void writeNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Line> lines = get(chapter);
        buffer.writeVarInt(lines.size());
        for (Line line : lines) {
            buffer.writeLong(line.source_id());
            buffer.writeLong(line.dependency_id());
            buffer.writeBoolean(line.reveal_on_hover());
        }
    }

    // 从 FTB Quests 的章节网络数据恢复隐藏前置线
    public static void readNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Line> lines = get(chapter);
        lines.clear();
        int line_count = Math.max(0, Math.min(buffer.readVarInt(), 4096));
        for (int index = 0; index < line_count; index++) {
            lines.add(new Line(buffer.readLong(), buffer.readLong(), buffer.readBoolean()));
        }
    }

    // 保存一条隐藏前置线的显示行为
    public record Line(long source_id, long dependency_id, boolean reveal_on_hover) {
    }
}
