package com.quest_enhance.common;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 保存章节背景图片资源，并负责章节存档与编辑同步数据的读写
public final class ChapterBackground {
    private static final String DATA_KEY = "quest_enhance_chapter_background";
    private static final Map<Chapter, Identifier> BACKGROUNDS = new WeakHashMap<>();

    private ChapterBackground() {
    }

    // 读取章节当前设置的背景图片资源
    public static Optional<Identifier> get(Chapter chapter) {
        return Optional.ofNullable(BACKGROUNDS.get(chapter));
    }

    // 更新章节背景图片，空值表示恢复默认背景
    public static void set(Chapter chapter, Identifier resource_id) {
        if (resource_id == null) {
            BACKGROUNDS.remove(chapter);
        } else {
            BACKGROUNDS.put(chapter, resource_id);
        }
    }

    // 将章节背景图片写入 JSON5 任务存档
    public static void writeData(Chapter chapter, Json5Object data) {
        get(chapter).ifPresent(resource_id -> data.addProperty(DATA_KEY, resource_id.toString()));
    }

    // 从 JSON5 任务存档恢复章节背景图片
    public static void readData(Chapter chapter, Json5Object data) {
        set(chapter, null);
        if (!data.has(DATA_KEY)) {
            return;
        }

        Identifier resource_id = Identifier.tryParse(data.get(DATA_KEY).getAsString());
        set(chapter, resource_id);
    }

    // 将章节背景图片追加到 FTB Quests 的编辑同步数据
    public static void writeNetData(Chapter chapter, RegistryFriendlyByteBuf buffer) {
        Optional<Identifier> resource_id = get(chapter);
        buffer.writeBoolean(resource_id.isPresent());
        resource_id.ifPresent(value -> buffer.writeUtf(value.toString(), 256));
    }

    // 从 FTB Quests 的编辑同步数据恢复章节背景图片
    public static void readNetData(Chapter chapter, RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            set(chapter, null);
            return;
        }

        set(chapter, Identifier.tryParse(buffer.readUtf(256)));
    }
}
