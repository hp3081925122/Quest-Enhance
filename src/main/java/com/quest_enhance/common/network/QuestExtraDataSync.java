package com.quest_enhance.common.network;

import com.quest_enhance.ChapterPrerequisites;
import com.quest_enhance.DecorativeDependencyLines;
import com.quest_enhance.HiddenDependencyLines;
import com.quest_enhance.QuestTaskRequirement;
import com.quest_enhance.common.ChapterBackground;
import com.quest_enhance.common.ChapterSidebarBackground;
import com.quest_enhance.common.QuestBackground;
import com.quest_enhance.common.QuestScrollPaging;
import com.quest_enhance.common.QuestViewBackground;
import com.quest_enhance.common.canvas.ChapterImageSelectionAccess;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

// Quest Enhance 扩展字段的独立同步数据，不再写入 FTB Quests 自己的同步流
public final class QuestExtraDataSync {
    public static final byte KIND_FILE = 0;
    public static final byte KIND_CHAPTER = 1;
    public static final byte KIND_QUEST = 2;
    public static final byte KIND_IMAGE = 3;

    // 单个数据包最多携带的条目数，避免单包过大
    public static final int MAXIMUM_ENTRIES_PER_PACKET = 256;
    private static final String IMAGE_EXCLUDED_KEY = "excluded";

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
            add(entries, KIND_CHAPTER, chapter.id, chapter_tag);

            for (ChapterImage image : chapter.getImages()) {
                if (((ChapterImageSelectionAccess) (Object) image).quest_enhance$is_excluded_from_box_selection()) {
                    CompoundTag image_tag = new CompoundTag();
                    image_tag.putBoolean(IMAGE_EXCLUDED_KEY, true);
                    add(entries, KIND_IMAGE, image.id, image_tag);
                }
            }

            for (Quest quest : chapter.getQuests()) {
                CompoundTag quest_tag = new CompoundTag();
                QuestBackground.writeData(quest, quest_tag);
                QuestViewBackground.writeData(quest, quest_tag);
                QuestScrollPaging.writeData(quest, quest_tag);
                QuestTaskRequirement.writeData(quest, quest_tag);
                add(entries, KIND_QUEST, quest.id, quest_tag);
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
                case KIND_IMAGE -> {
                    if (file.getBase(entry.id()) instanceof ChapterImage image) {
                        ((ChapterImageSelectionAccess) (Object) image).quest_enhance$set_excluded_from_box_selection(
                                entry.tag().getBoolean(IMAGE_EXCLUDED_KEY)
                        );
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

    // 文件条目总是发送以便重置；其它对象的空条目代表默认值，客户端新建对象本身就是默认值
    private static void add(List<Entry> entries, byte kind, long id, CompoundTag tag) {
        if (kind == KIND_FILE || !tag.isEmpty()) {
            entries.add(new Entry(kind, id, tag));
        }
    }

    public record Entry(byte kind, long id, CompoundTag tag) {
    }
}
