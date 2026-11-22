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
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.util.FTBQCodecs;
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
        Json5Object file_data = new Json5Object();
        ChapterSidebarBackground.writeData(file, file_data);
        add(entries, KIND_FILE, file.id, file_data);

        for (Chapter chapter : file.getAllChapters()) {
            Json5Object chapter_data = new Json5Object();
            DecorativeDependencyLines.writeData(chapter, chapter_data);
            HiddenDependencyLines.writeData(chapter, chapter_data);
            ChapterBackground.writeData(chapter, chapter_data);
            ChapterPrerequisites.writeData(chapter, chapter_data);
            add(entries, KIND_CHAPTER, chapter.id, chapter_data);

            for (ChapterImage image : chapter.getImages()) {
                if (((ChapterImageSelectionAccess) (Object) image).quest_enhance$is_excluded_from_box_selection()) {
                    Json5Object image_data = new Json5Object();
                    image_data.addProperty(IMAGE_EXCLUDED_KEY, true);
                    add(entries, KIND_IMAGE, image.id, image_data);
                }
            }

            for (Quest quest : chapter.getQuests()) {
                Json5Object quest_data = new Json5Object();
                QuestBackground.writeData(quest, quest_data);
                QuestViewBackground.writeData(quest, quest_data);
                QuestScrollPaging.writeData(quest, quest_data);
                QuestTaskRequirement.writeData(quest, quest_data);
                add(entries, KIND_QUEST, quest.id, quest_data);
            }
        }
        return entries;
    }

    // 在客户端把服务端发来的扩展字段写回对应对象
    public static void apply(BaseQuestFile file, List<Entry> entries) {
        for (Entry entry : entries) {
            switch (entry.kind()) {
                case KIND_FILE -> ChapterSidebarBackground.readData(file, entry.data());
                case KIND_CHAPTER -> {
                    if (file.getBase(entry.id()) instanceof Chapter chapter) {
                        DecorativeDependencyLines.readData(chapter, entry.data());
                        HiddenDependencyLines.readData(chapter, entry.data());
                        ChapterBackground.readData(chapter, entry.data());
                        ChapterPrerequisites.readData(chapter, entry.data());
                    }
                }
                case KIND_QUEST -> {
                    if (file.getBase(entry.id()) instanceof Quest quest) {
                        QuestBackground.readData(quest, entry.data());
                        QuestViewBackground.readData(quest, entry.data());
                        QuestScrollPaging.readData(quest, entry.data());
                        QuestTaskRequirement.readData(quest, entry.data());
                    }
                }
                case KIND_IMAGE -> {
                    if (file.getBase(entry.id()) instanceof ChapterImage image) {
                        boolean excluded = entry.data().has(IMAGE_EXCLUDED_KEY)
                                && entry.data().get(IMAGE_EXCLUDED_KEY).getAsBoolean();
                        ((ChapterImageSelectionAccess) (Object) image)
                                .quest_enhance$set_excluded_from_box_selection(excluded);
                    }
                }
                default -> {
                }
            }
        }
    }

    // 编码一批扩展字段条目，复用 FTB Quests 自带的 JSON5 网络编解码
    public static void write(FriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeByte(entry.kind());
            buffer.writeLong(entry.id());
            FTBQCodecs.JSON5_OBJECT_STREAM_CODEC.encode(buffer, entry.data());
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
            Json5Object data = FTBQCodecs.JSON5_OBJECT_STREAM_CODEC.decode(buffer);
            entries.add(new Entry(kind, id, data == null ? new Json5Object() : data));
        }
        return entries;
    }

    // 文件条目总是发送以便重置；其它对象的空条目代表默认值，客户端新建对象本身就是默认值
    private static void add(List<Entry> entries, byte kind, long id, Json5Object data) {
        if (kind == KIND_FILE || !data.keySet().isEmpty()) {
            entries.add(new Entry(kind, id, data));
        }
    }

    public record Entry(byte kind, long id, Json5Object data) {
    }
}
