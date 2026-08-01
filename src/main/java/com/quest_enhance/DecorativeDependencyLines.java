package com.quest_enhance;

import de.marhali.json5.Json5Array;
import de.marhali.json5.Json5Element;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;
import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 管理章节中的装饰性多节点折线，不参与任务依赖计算
public final class DecorativeDependencyLines {
    private static final String NBT_KEY = "quest_enhance_decorative_lines";
    private static final String NODES_KEY = "nodes";
    private static final String QUEST_PREFIX = "q:";
    private static final String QUEST_LINK_PREFIX = "l:";
    private static final Map<Chapter, List<Line>> LINES = new WeakHashMap<>();

    private DecorativeDependencyLines() {
    }

    // 读取章节当前的装饰线列表
    public static List<Line> get(Chapter chapter) {
        return LINES.computeIfAbsent(chapter, ignored -> new ArrayList<>());
    }

    // 将任务编号转换为不会与辅助点冲突的节点键
    public static String questNode(long quest_id) {
        return QUEST_PREFIX + Long.toUnsignedString(quest_id, 16);
    }

    // 将链接任务编号转换为独立于真实任务的节点键
    public static String questLinkNode(long link_id) {
        return QUEST_LINK_PREFIX + Long.toUnsignedString(link_id, 16);
    }

    // 添加一条按选择顺序排列的多节点装饰线
    public static boolean add(Chapter chapter, List<String> node_keys) {
        List<String> normalized = normalize(node_keys);
        if (normalized.size() < 2 || find(chapter, normalized).isPresent()) {
            return false;
        }
        get(chapter).add(new Line(normalized));
        QuestEnhance.LOGGER.debug("Added decorative line: selectedNodes={}", normalized);
        return true;
    }

    // 删除包含当前全部选择节点的装饰线
    public static boolean remove(Chapter chapter, List<String> node_keys) {
        List<String> normalized = normalize(node_keys);
        Iterator<Line> iterator = get(chapter).iterator();
        while (iterator.hasNext()) {
            Line line = iterator.next();
            if (containsNodes(line.nodes(), normalized)) {
                iterator.remove();
                QuestEnhance.LOGGER.debug(
                        "Removed decorative line: selectedNodes={}, lineNodes={}",
                        normalized,
                        line.nodes()
                );
                return true;
            }
        }
        QuestEnhance.LOGGER.debug("No decorative line matched selected nodes: {}", normalized);
        return false;
    }

    // 查找包含当前全部选择节点的装饰线
    public static Optional<Line> find(Chapter chapter, List<String> node_keys) {
        List<String> normalized = normalize(node_keys);
        return get(chapter).stream()
                .filter(line -> containsNodes(line.nodes(), normalized))
                .findFirst();
    }

    // 删除节点时从全部装饰线中同步移除，节点不足两个的线一并删除
    public static void removeNode(Chapter chapter, String node_key) {
        List<Line> lines = get(chapter);
        for (int index = lines.size() - 1; index >= 0; index--) {
            Line line = lines.get(index);
            if (!line.nodes().contains(node_key)) {
                continue;
            }
            List<String> nodes = new ArrayList<>(line.nodes());
            nodes.removeIf(node_key::equals);
            if (nodes.size() < 2) {
                lines.remove(index);
            } else {
                lines.set(index, new Line(List.copyOf(nodes)));
            }
        }
    }

    // 将装饰线写入章节存档数据
    public static void writeData(Chapter chapter, Json5Object tag) {
        Json5Array line_list = new Json5Array();
        for (Line line : get(chapter)) {
            Json5Object line_tag = new Json5Object();
            Json5Array nodes = new Json5Array();
            for (String node : line.nodes()) {
                nodes.add(node);
            }
            line_tag.add(NODES_KEY, nodes);
            line_list.add(line_tag);
        }
        tag.add(NBT_KEY, line_list);
    }

    // 从章节存档数据读取装饰线，并迁移旧版任务编号数组
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
            List<String> nodes = new ArrayList<>();
            Json5Element raw_nodes = line_tag.get(NODES_KEY);
            if (raw_nodes == null || !raw_nodes.isJson5Array()) {
                continue;
            }
            for (Json5Element raw_node : raw_nodes.getAsJson5Array()) {
                if (!raw_node.isJson5Primitive()) {
                    continue;
                }
                Json5Primitive node = raw_node.getAsJson5Primitive();
                if (node.isString()) {
                    nodes.add(node.getAsString());
                } else if (node.isNumber()) {
                    nodes.add(questNode(node.getAsLong()));
                }
            }
            List<String> normalized = normalize(nodes);
            if (normalized.size() >= 2) {
                lines.add(new Line(normalized));
            }
        }
    }

    // 将装饰线写入 FTB Quests 的章节网络数据
    public static void writeNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Line> lines = get(chapter);
        buffer.writeVarInt(lines.size());
        for (Line line : lines) {
            buffer.writeVarInt(line.nodes().size());
            for (String node : line.nodes()) {
                buffer.writeUtf(node, 128);
            }
        }
    }

    // 从 FTB Quests 的章节网络数据恢复装饰线
    public static void readNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Line> lines = get(chapter);
        lines.clear();
        int line_count = Math.max(0, Math.min(buffer.readVarInt(), 4096));
        for (int index = 0; index < line_count; index++) {
            int node_count = Math.max(0, Math.min(buffer.readVarInt(), 4096));
            List<String> nodes = new ArrayList<>(node_count);
            for (int node_index = 0; node_index < node_count; node_index++) {
                nodes.add(buffer.readUtf(128));
            }
            List<String> normalized = normalize(nodes);
            if (normalized.size() >= 2) {
                lines.add(new Line(normalized));
            }
        }
    }

    // 按首次出现顺序去除重复和空节点键
    private static List<String> normalize(List<String> node_keys) {
        List<String> normalized = new ArrayList<>(node_keys.size());
        for (String node : node_keys) {
            if (node != null && !node.isBlank() && !normalized.contains(node)) {
                normalized.add(node);
            }
        }
        return List.copyOf(normalized);
    }

    // 判断一条装饰线是否包含当前全部选择节点
    private static boolean containsNodes(List<String> line_nodes, List<String> selected_nodes) {
        return line_nodes.containsAll(selected_nodes);
    }

    // 保存单条装饰线的有序节点
    public record Line(List<String> nodes) {
    }
}
