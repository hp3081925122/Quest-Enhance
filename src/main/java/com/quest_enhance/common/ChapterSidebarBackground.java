package com.quest_enhance.common;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 保存任务书章节侧边栏背景图片，并负责存档和网络同步数据的读写
public final class ChapterSidebarBackground {
    private static final String DATA_KEY = "quest_enhance_chapter_sidebar_background";
    private static final Map<BaseQuestFile, Identifier> BACKGROUNDS = new WeakHashMap<>();

    private ChapterSidebarBackground() {
    }

    // 读取任务书当前设置的章节侧边栏背景图片
    public static Optional<Identifier> get(BaseQuestFile file) {
        return Optional.ofNullable(BACKGROUNDS.get(file));
    }

    // 更新任务书章节侧边栏背景图片，空值表示恢复默认背景
    public static void set(BaseQuestFile file, Identifier resource_id) {
        if (resource_id == null) {
            BACKGROUNDS.remove(file);
        } else {
            BACKGROUNDS.put(file, resource_id);
        }
    }

    // 将章节侧边栏背景图片写入任务书 JSON5 存档
    public static void writeData(BaseQuestFile file, Json5Object data) {
        get(file).ifPresent(resource_id -> data.addProperty(DATA_KEY, resource_id.toString()));
    }

    // 从任务书 JSON5 存档恢复章节侧边栏背景图片
    public static void readData(BaseQuestFile file, Json5Object data) {
        set(file, null);
        if (!data.has(DATA_KEY)) {
            return;
        }

        set(file, Identifier.tryParse(data.get(DATA_KEY).getAsString()));
    }

    // 将章节侧边栏背景图片写入任务书网络同步数据
    public static void writeNetData(BaseQuestFile file, RegistryFriendlyByteBuf buffer) {
        Optional<Identifier> resource_id = get(file);
        buffer.writeBoolean(resource_id.isPresent());
        resource_id.ifPresent(value -> buffer.writeUtf(value.toString(), 256));
    }

    // 从任务书网络同步数据恢复章节侧边栏背景图片
    public static void readNetData(BaseQuestFile file, RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            set(file, null);
            return;
        }

        set(file, Identifier.tryParse(buffer.readUtf(256)));
    }
}
