package com.quest_enhance;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// 保存章节的任务节点前置关系，并负责同步、清理和可见性判断
public final class ChapterPrerequisites {
    private static final String NBT_KEY = "quest_enhance_chapter_quest_prerequisites";
    private static final Map<Chapter, LinkedHashSet<Long>> PREREQUISITES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ChapterPrerequisites() {
    }

    // 获取章节前置任务 ID 的稳定副本，避免界面直接修改内部集合
    public static List<Long> getPrerequisiteIds(Chapter chapter) {
        if (chapter == null) {
            return List.of();
        }
        Set<Long> ids = PREREQUISITES.get(chapter);
        return ids == null ? List.of() : List.copyOf(ids);
    }

    // 将章节前置 ID 解析为当前任务书中的普通任务节点
    public static List<Quest> getPrerequisites(Chapter chapter) {
        if (chapter == null) {
            return List.of();
        }

        List<Quest> prerequisites = new ArrayList<>();
        BaseQuestFile file = chapter.getQuestFile();
        for (long id : getPrerequisiteIds(chapter)) {
            Quest prerequisite = file.getQuest(id);
            if (prerequisite != null) {
                prerequisites.add(prerequisite);
            }
        }
        return List.copyOf(prerequisites);
    }

    // 获取允许添加的普通任务节点，排除当前章节内的任务以避免章节自锁
    public static List<Quest> getAvailablePrerequisites(Chapter chapter) {
        if (chapter == null) {
            return List.of();
        }

        List<Quest> available = new ArrayList<>();
        for (Chapter source_chapter : chapter.getQuestFile().getAllChapters()) {
            for (Quest candidate : source_chapter.getQuests()) {
                if (canAdd(chapter, candidate)) {
                    available.add(candidate);
                }
            }
        }
        return List.copyOf(available);
    }

    // 判断一个任务节点是否可以作为章节前置
    public static boolean canAdd(Chapter chapter, Quest prerequisite) {
        if (chapter == null || prerequisite == null
                || chapter.getQuestFile() != prerequisite.getQuestFile()
                || prerequisite.getChapter() == chapter) {
            return false;
        }

        return !getPrerequisiteIds(chapter).contains(prerequisite.getId());
    }

    // 添加章节任务节点前置关系
    public static boolean add(Chapter chapter, Quest prerequisite) {
        if (chapter == null || prerequisite == null) {
            return false;
        }
        if (getPrerequisiteIds(chapter).contains(prerequisite.getId())) {
            return true;
        }
        if (!canAdd(chapter, prerequisite)) {
            return false;
        }

        LinkedHashSet<Long> ids = new LinkedHashSet<>(getPrerequisiteIds(chapter));
        ids.add(prerequisite.getId());
        replace(chapter, ids);
        return true;
    }

    // 删除一个章节任务节点前置关系
    public static boolean remove(Chapter chapter, Quest prerequisite) {
        if (chapter == null || prerequisite == null) {
            return false;
        }
        return remove(chapter, prerequisite.getId());
    }

    // 按任务节点 ID 删除一个章节前置关系
    public static boolean remove(Chapter chapter, long prerequisite_id) {
        if (chapter == null) {
            return false;
        }

        LinkedHashSet<Long> ids = PREREQUISITES.get(chapter);
        if (ids == null || !ids.remove(prerequisite_id)) {
            return false;
        }
        if (ids.isEmpty()) {
            PREREQUISITES.remove(chapter);
        }
        return true;
    }

    // 清除章节的全部任务节点前置
    public static boolean clear(Chapter chapter) {
        return chapter != null && PREREQUISITES.remove(chapter) != null;
    }

    // 判断指定队伍是否完成章节的全部任务节点前置
    public static boolean arePrerequisitesCompleted(Chapter chapter, TeamData team_data) {
        if (chapter == null) {
            return false;
        }

        List<Long> ids = getPrerequisiteIds(chapter);
        if (ids.isEmpty()) {
            return true;
        }
        if (team_data == null) {
            return false;
        }

        for (long id : ids) {
            Quest prerequisite = chapter.getQuestFile().getQuest(id);
            if (prerequisite == null
                    || prerequisite.getChapter() == chapter
                    || !team_data.isCompleted(prerequisite)) {
                return false;
            }
        }
        return true;
    }

    // 将章节任务节点前置写入章节存档
    public static void writeData(Chapter chapter, CompoundTag tag) {
        List<Long> ids = getPrerequisiteIds(chapter);
        if (ids.isEmpty()) {
            tag.remove(NBT_KEY);
            return;
        }

        ListTag prerequisite_list = new ListTag();
        for (long id : ids) {
            prerequisite_list.add(LongTag.valueOf(id));
        }
        tag.put(NBT_KEY, prerequisite_list);
    }

    // 从章节存档读取任务节点前置
    public static void readData(Chapter chapter, CompoundTag tag) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ListTag prerequisite_list = tag.getList(NBT_KEY, Tag.TAG_LONG);
        for (int index = 0; index < prerequisite_list.size(); index++) {
            long id = ((LongTag) prerequisite_list.get(index)).getAsLong();
            if (id != chapter.getId()) {
                ids.add(id);
            }
        }
        replace(chapter, ids);
    }

    // 将章节任务节点前置追加到 FTB Quests 的章节网络数据
    public static void writeNetData(Chapter chapter, FriendlyByteBuf buffer) {
        List<Long> ids = getPrerequisiteIds(chapter);
        buffer.writeVarInt(ids.size());
        for (long id : ids) {
            buffer.writeLong(id);
        }
    }

    // 从 FTB Quests 的章节网络数据恢复任务节点前置
    public static void readNetData(Chapter chapter, FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0) {
            throw new IllegalArgumentException("Negative chapter quest prerequisite count");
        }

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            long id = buffer.readLong();
            if (id != chapter.getId()) {
                ids.add(id);
            }
        }
        replace(chapter, ids);
    }

    // 删除任务节点时清理所有章节对它的前置引用
    public static void removeQuest(Quest quest) {
        if (quest == null) {
            return;
        }

        for (Chapter chapter : quest.getQuestFile().getAllChapters()) {
            remove(chapter, quest.getId());
        }
    }

    // 删除章节时清理该章节拥有的任务节点前置引用
    public static void removeChapter(Chapter chapter) {
        if (chapter == null) {
            return;
        }

        List<Long> quest_ids = chapter.getQuests().stream()
                .map(Quest::getId)
                .toList();
        for (Chapter other : chapter.getQuestFile().getAllChapters()) {
            if (other != chapter) {
                for (long quest_id : quest_ids) {
                    remove(other, quest_id);
                }
            }
        }
        PREREQUISITES.remove(chapter);
    }

    // 替换章节任务节点前置集合，并在空集合时释放映射项
    private static void replace(Chapter chapter, Set<Long> ids) {
        if (ids.isEmpty()) {
            PREREQUISITES.remove(chapter);
        } else {
            PREREQUISITES.put(chapter, new LinkedHashSet<>(ids));
        }
    }
}
