package com.quest_enhance.common.network;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.ChapterBackground;
import com.quest_enhance.ChapterPrerequisites;
import com.quest_enhance.ChapterSidebarBackground;
import com.quest_enhance.DecorativeDependencyLines;
import com.quest_enhance.HiddenDependencyLines;
import com.quest_enhance.QuestBackground;
import com.quest_enhance.QuestScrollPaging;
import com.quest_enhance.QuestTaskRequirement;
import com.quest_enhance.QuestViewBackground;
import com.quest_enhance.access.KillTaskAccessor;
import com.quest_enhance.common.canvas.ChapterImageSelectionAccess;
import com.quest_enhance.compat.QuestsAdditionsCompatibility;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.task.Task;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

// Quest Enhance 扩展字段的独立同步数据，不再写入 FTB Quests 自己的同步流
public final class QuestExtraDataSync {
    public static final byte KIND_FILE = 0;
    public static final byte KIND_CHAPTER = 1;
    public static final byte KIND_QUEST = 2;
    public static final byte KIND_KILL_TASK = 3;

    // 单个数据包最多携带的条目数，避免超出 Forge 自定义包体积上限
    public static final int MAXIMUM_ENTRIES_PER_PACKET = 256;
    // 留出包头余量，低于 1048576 字节上限
    private static final int MAXIMUM_BATCH_BYTES = 900_000;
    private static final String IMAGE_SELECTION_KEY = "quest_enhance_sync_image_selection";
    private static final String ENTITY_TYPE_TAG_KEY = "entityTypeTag";

    private QuestExtraDataSync() {
    }

    // 收集任务书中所有带有扩展字段的对象
    public static List<Entry> collect(BaseQuestFile file) {
        List<Entry> entries = new ArrayList<>();
        CompoundTag file_tag = new CompoundTag();
        ChapterSidebarBackground.writeData(file, file_tag);
        add(entries, KIND_FILE, file.id, file_tag);

        for (Chapter chapter : file.getAllChapters()) {
            CompoundTag chapter_tag = new CompoundTag();
            DecorativeDependencyLines.writeData(chapter, chapter_tag);
            HiddenDependencyLines.writeData(chapter, chapter_tag);
            ChapterBackground.writeData(chapter, chapter_tag);
            ChapterPrerequisites.writeData(chapter, chapter_tag);
            ListTag image_flags = new ListTag();
            boolean has_excluded_image = false;
            for (ChapterImage image : chapter.getImages()) {
                boolean excluded = ((ChapterImageSelectionAccess) (Object) image).quest_enhance$is_excluded_from_box_selection();
                has_excluded_image |= excluded;
                image_flags.add(ByteTag.valueOf(excluded));
            }
            if (has_excluded_image) {
                chapter_tag.put(IMAGE_SELECTION_KEY, image_flags);
            }
            add(entries, KIND_CHAPTER, chapter.id, chapter_tag);

            for (Quest quest : chapter.getQuests()) {
                CompoundTag quest_tag = new CompoundTag();
                QuestBackground.writeData(quest, quest_tag);
                QuestViewBackground.writeData(quest, quest_tag);
                QuestScrollPaging.writeData(quest, quest_tag);
                QuestTaskRequirement.writeData(quest, quest_tag);
                add(entries, KIND_QUEST, quest.id, quest_tag);

                for (Task task : quest.getTasks()) {
                    if (task instanceof KillTaskAccessor accessor && !QuestsAdditionsCompatibility.isKillNbtTask(task)
                            && accessor.quest_enhance$get_entity_type_tag() != null) {
                        CompoundTag task_tag = new CompoundTag();
                        task_tag.putString(
                                ENTITY_TYPE_TAG_KEY,
                                accessor.quest_enhance$get_entity_type_tag().location().toString()
                        );
                        add(entries, KIND_KILL_TASK, task.id, task_tag);
                    }
                }
            }
        }
        return entries;
    }

    // 在客户端把服务端发来的扩展字段写回对应对象
    public static void apply(BaseQuestFile file, List<Entry> entries) {
        for (Entry entry : entries) {
            switch (entry.kind()) {
                case KIND_FILE -> ChapterSidebarBackground.readData(file, entry.tag());
                case KIND_CHAPTER -> {
                    if (file.getBase(entry.id()) instanceof Chapter chapter) {
                        DecorativeDependencyLines.readData(chapter, entry.tag());
                        HiddenDependencyLines.readData(chapter, entry.tag());
                        ChapterBackground.readData(chapter, entry.tag());
                        ChapterPrerequisites.readData(chapter, entry.tag());
                        ListTag image_flags = entry.tag().getList(IMAGE_SELECTION_KEY, Tag.TAG_BYTE);
                        List<ChapterImage> images = chapter.getImages();
                        for (int index = 0; index < images.size(); index++) {
                            boolean excluded = index < image_flags.size()
                                    && ((ByteTag) image_flags.get(index)).getAsByte() != 0;
                            ((ChapterImageSelectionAccess) (Object) images.get(index))
                                    .quest_enhance$set_excluded_from_box_selection(excluded);
                        }
                    }
                }
                case KIND_QUEST -> {
                    if (file.getBase(entry.id()) instanceof Quest quest) {
                        QuestBackground.readData(quest, entry.tag());
                        QuestViewBackground.readData(quest, entry.tag());
                        QuestScrollPaging.readData(quest, entry.tag());
                        QuestTaskRequirement.readData(quest, entry.tag());
                    }
                }
                case KIND_KILL_TASK -> {
                    QuestObjectBase object = file.getBase(entry.id());
                    if (object instanceof KillTaskAccessor accessor && !QuestsAdditionsCompatibility.isKillNbtTask(object)) {
                        accessor.quest_enhance$set_entity_type_tag_id(entry.tag().getString(ENTITY_TYPE_TAG_KEY));
                    }
                }
                default -> {
                }
            }
        }
    }

    // 编码一批扩展字段条目，每个条目单独写 NBT 以使用独立的大小计数
    public static void write(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeByte(entry.kind());
            buffer.writeLong(entry.id());
            buffer.writeNbt(entry.tag());
        }
    }

    // 解码一批扩展字段条目
    public static List<Entry> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAXIMUM_ENTRIES_PER_PACKET) {
            throw new IllegalArgumentException("Invalid quest extra data entry count: " + count);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            byte kind = buffer.readByte();
            long id = buffer.readLong();
            CompoundTag tag = buffer.readNbt();
            entries.add(new Entry(kind, id, tag == null ? new CompoundTag() : tag));
        }
        return entries;
    }

    // 按条目数和编码后字节数分批，单批不超过 Forge 1.20.1 自定义包的 1 MiB 上限
    public static List<List<Entry>> split(List<Entry> entries) {
        List<List<Entry>> batches = new ArrayList<>();
        List<Entry> batch = new ArrayList<>();
        int batch_bytes = 0;
        FriendlyByteBuf measure = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Entry entry : entries) {
                measure.clear();
                measure.writeByte(entry.kind());
                measure.writeLong(entry.id());
                measure.writeNbt(entry.tag());
                int entry_bytes = measure.readableBytes();
                if (entry_bytes > MAXIMUM_BATCH_BYTES) {
                    QuestEnhance.LOGGER.warn(
                            "Skipped Quest Enhance extra data for object {} because it is {} bytes",
                            QuestObjectBase.getCodeString(entry.id()),
                            entry_bytes
                    );
                    continue;
                }
                if (!batch.isEmpty() && (batch.size() >= MAXIMUM_ENTRIES_PER_PACKET
                        || batch_bytes + entry_bytes > MAXIMUM_BATCH_BYTES)) {
                    batches.add(batch);
                    batch = new ArrayList<>();
                    batch_bytes = 0;
                }
                batch.add(entry);
                batch_bytes += entry_bytes;
            }
        } finally {
            measure.release();
        }
        if (!batch.isEmpty()) {
            batches.add(batch);
        }
        return batches;
    }

    // 文件条目总是发送以便重置；其它对象的空条目代表默认值，客户端新建对象本身就是默认值
    private static void add(List<Entry> entries, byte kind, long id, CompoundTag tag) {
        if (kind == KIND_FILE || !tag.isEmpty()) {
            entries.add(new Entry(kind, id, tag));
        }
    }

    public record Entry(byte kind, long id, CompoundTag tag) {
    }
}
