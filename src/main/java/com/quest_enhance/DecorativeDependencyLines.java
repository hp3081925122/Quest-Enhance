package com.quest_enhance;

import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    private static final String OUTLINE_KEY = "outline";
    private static final String SHADOW_KEY = "shadow";
    private static final String QUEST_PREFIX = "q:";
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

    // 添加一条按选择顺序排列的多节点装饰线
    public static boolean add(Chapter chapter, List<String> node_keys) {
        List<String> normalized = normalize(node_keys);
        if (normalized.size() < 2 || find(chapter, normalized).isPresent()) {
            return false;
        }
        get(chapter).add(new Line(normalized, true, true));
        return true;
    }

    // 删除与当前选择节点集合相同的装饰线
    public static boolean remove(Chapter chapter, List<String> node_keys) {
        List<String> normalized = normalize(node_keys);
        Iterator<Line> iterator = get(chapter).iterator();
        while (iterator.hasNext()) {
            if (sameNodes(iterator.next().nodes(), normalized)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    // 查找与当前选择节点集合相同的装饰线
    public static Optional<Line> find(Chapter chapter, List<String> node_keys) {
        List<String> normalized = normalize(node_keys);
        return get(chapter).stream()
                .filter(line -> sameNodes(line.nodes(), normalized))
                .findFirst();
    }

    // 切换指定装饰线的描边状态
    public static boolean setOutline(Chapter chapter, List<String> node_keys, boolean outline) {
        List<String> normalized = normalize(node_keys);
        List<Line> lines = get(chapter);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (sameNodes(line.nodes(), normalized) && line.outline() != outline) {
                lines.set(index, new Line(line.nodes(), outline, line.shadow()));
                return true;
            }
        }
        return false;
    }

    // 切换指定装饰线的阴影状态
    public static boolean setShadow(Chapter chapter, List<String> node_keys, boolean shadow) {
        List<String> normalized = normalize(node_keys);
        List<Line> lines = get(chapter);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (sameNodes(line.nodes(), normalized) && line.shadow() != shadow) {
                lines.set(index, new Line(line.nodes(), line.outline(), shadow));
                return true;
            }
        }
        return false;
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
                lines.set(index, new Line(List.copyOf(nodes), line.outline(), line.shadow()));
            }
        }
    }

    // 将装饰线写入章节存档数据
    public static void writeData(Chapter chapter, CompoundTag tag) {
        ListTag line_list = new ListTag();
        for (Line line : get(chapter)) {
            CompoundTag line_tag = new CompoundTag();
            ListTag nodes = new ListTag();
            for (String node : line.nodes()) {
                nodes.add(StringTag.valueOf(node));
            }
            line_tag.put(NODES_KEY, nodes);
            line_tag.putBoolean(OUTLINE_KEY, line.outline());
            line_tag.putBoolean(SHADOW_KEY, line.shadow());
            line_list.add(line_tag);
        }
        tag.put(NBT_KEY, line_list);
    }

    // 从章节存档数据读取装饰线，并迁移旧版任务编号数组
    public static void readData(Chapter chapter, CompoundTag tag) {
        List<Line> lines = get(chapter);
        lines.clear();
        if (!tag.contains(NBT_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag line_list = tag.getList(NBT_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < line_list.size(); index++) {
            CompoundTag line_tag = line_list.getCompound(index);
            List<String> nodes = new ArrayList<>();
            if (line_tag.contains(NODES_KEY, Tag.TAG_LIST)) {
                ListTag node_list = line_tag.getList(NODES_KEY, Tag.TAG_STRING);
                for (int node_index = 0; node_index < node_list.size(); node_index++) {
                    nodes.add(node_list.getString(node_index));
                }
            } else if (line_tag.contains(NODES_KEY, Tag.TAG_LONG_ARRAY)) {
                for (long node : line_tag.getLongArray(NODES_KEY)) {
                    nodes.add(questNode(node));
                }
            }
            List<String> normalized = normalize(nodes);
            if (normalized.size() >= 2) {
                lines.add(new Line(
                        normalized,
                        line_tag.getBoolean(OUTLINE_KEY),
                        line_tag.getBoolean(SHADOW_KEY)
                ));
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
            buffer.writeBoolean(line.outline());
            buffer.writeBoolean(line.shadow());
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
            boolean outline = buffer.readBoolean();
            boolean shadow = buffer.readBoolean();
            List<String> normalized = normalize(nodes);
            if (normalized.size() >= 2) {
                lines.add(new Line(normalized, outline, shadow));
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

    // 判断两条线是否连接了同一组节点
    private static boolean sameNodes(List<String> first, List<String> second) {
        return first.size() == second.size() && first.containsAll(second);
    }

    // 保存单条装饰线的有序节点和视觉样式
    public record Line(List<String> nodes, boolean outline, boolean shadow) {
    }
}
