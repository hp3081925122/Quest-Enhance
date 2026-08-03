package com.quest_enhance;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 管理章节中按画布节点与前置节点区分的隐藏前置线
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

    // 将任务和链接任务转换为互不冲突的画布节点键
    public static String nodeKey(Movable movable) {
        if (movable instanceof Quest quest) {
            return DecorativeDependencyLines.questNode(quest.getMovableID());
        }
        if (movable instanceof QuestLink link) {
            return DecorativeDependencyLines.questLinkNode(link.getMovableID());
        }
        throw new IllegalArgumentException("Unsupported hidden dependency line node: " + movable.getClass().getName());
    }

    // 查找指定画布节点到前置节点的隐藏线设置
    public static Optional<Line> find(Chapter chapter, String source_node, String dependency_node) {
        return get(chapter).stream()
                .filter(line -> line.source_node().equals(source_node)
                        && line.dependency_node().equals(dependency_node))
                .findFirst();
    }

    // 更新一条前置线的隐藏和悬停显示设置
    public static void set(
            Chapter chapter,
            String source_node,
            String dependency_node,
            boolean hidden,
            boolean reveal_on_hover
    ) {
        List<Line> lines = get(chapter);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (!line.source_node().equals(source_node) || !line.dependency_node().equals(dependency_node)) {
                continue;
            }
            if (hidden) {
                lines.set(index, new Line(source_node, dependency_node, reveal_on_hover));
            } else {
                lines.remove(index);
            }
            return;
        }
        if (hidden) {
            lines.add(new Line(source_node, dependency_node, reveal_on_hover));
        }
    }

    // 删除画布节点时清理所有与之相连的隐藏前置线
    public static void removeNode(Chapter chapter, String node_key) {
        get(chapter).removeIf(line -> line.source_node().equals(node_key)
                || line.dependency_node().equals(node_key));
    }

    // 将隐藏前置线写入章节存档数据
    public static void writeData(Chapter chapter, CompoundTag tag) {
        ListTag line_list = new ListTag();
        for (Line line : get(chapter)) {
            CompoundTag line_tag = new CompoundTag();
            line_tag.putString(SOURCE_KEY, line.source_node());
            line_tag.putString(DEPENDENCY_KEY, line.dependency_node());
            line_tag.putBoolean(REVEAL_ON_HOVER_KEY, line.reveal_on_hover());
            line_list.add(line_tag);
        }
        tag.put(NBT_KEY, line_list);
    }

    // 从章节存档数据恢复隐藏前置线
    public static void readData(Chapter chapter, CompoundTag tag) {
        List<Line> lines = get(chapter);
        lines.clear();
        if (!tag.contains(NBT_KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag line_list = tag.getList(NBT_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < line_list.size(); index++) {
            CompoundTag line_tag = line_list.getCompound(index);
            String source_node = readNodeKey(line_tag, SOURCE_KEY);
            String dependency_node = readNodeKey(line_tag, DEPENDENCY_KEY);
            if (source_node == null || dependency_node == null) {
                continue;
            }
            lines.add(new Line(
                    source_node,
                    dependency_node,
                    line_tag.getBoolean(REVEAL_ON_HOVER_KEY)
            ));
        }
    }

    // 读取新节点键格式，并将旧版纯任务编号迁移为任务节点键
    private static String readNodeKey(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_STRING)) {
            String node_key = tag.getString(key);
            return node_key.isBlank() ? null : node_key;
        }
        if (tag.contains(key, Tag.TAG_LONG)) {
            return DecorativeDependencyLines.questNode(tag.getLong(key));
        }
        return null;
    }

    // 将隐藏前置线写入 FTB Quests 的章节网络数据
    public static void writeNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Line> lines = get(chapter);
        buffer.writeVarInt(lines.size());
        for (Line line : lines) {
            buffer.writeUtf(line.source_node(), 128);
            buffer.writeUtf(line.dependency_node(), 128);
            buffer.writeBoolean(line.reveal_on_hover());
        }
    }

    // 从 FTB Quests 的章节网络数据恢复隐藏前置线
    public static void readNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Line> lines = get(chapter);
        lines.clear();
        int line_count = Math.max(0, Math.min(buffer.readVarInt(), 4096));
        for (int index = 0; index < line_count; index++) {
            lines.add(new Line(buffer.readUtf(128), buffer.readUtf(128), buffer.readBoolean()));
        }
    }

    // 保存一条隐藏前置线的显示行为
    public record Line(String source_node, String dependency_node, boolean reveal_on_hover) {
    }
}
