package com.quest_enhance.common;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 保存任务详情面板背景图片，并负责任务节点存档和网络同步数据的读写
public final class QuestViewBackground {
    private static final String DATA_KEY = "quest_enhance_quest_view_background";
    private static final Map<Quest, Identifier> BACKGROUNDS = new WeakHashMap<>();

    private QuestViewBackground() {
    }

    // 读取任务详情面板当前设置的背景图片
    public static Optional<Identifier> get(Quest quest) {
        return Optional.ofNullable(BACKGROUNDS.get(quest));
    }

    // 更新任务详情面板背景图片，空值表示恢复原版背景
    public static void set(Quest quest, Identifier resource_id) {
        if (resource_id == null) {
            BACKGROUNDS.remove(quest);
        } else {
            BACKGROUNDS.put(quest, resource_id);
        }
    }

    // 将任务详情面板背景图片写入任务书 JSON5 存档
    public static void writeData(Quest quest, Json5Object data) {
        get(quest).ifPresent(resource_id -> data.addProperty(DATA_KEY, resource_id.toString()));
    }

    // 从任务书 JSON5 存档恢复任务详情面板背景图片
    public static void readData(Quest quest, Json5Object data) {
        set(quest, null);
        if (!data.has(DATA_KEY)) {
            return;
        }

        set(quest, Identifier.tryParse(data.get(DATA_KEY).getAsString()));
    }

    // 将任务详情面板背景图片写入任务书网络同步数据
    public static void writeNetData(Quest quest, RegistryFriendlyByteBuf buffer) {
        Optional<Identifier> resource_id = get(quest);
        buffer.writeBoolean(resource_id.isPresent());
        resource_id.ifPresent(value -> buffer.writeUtf(value.toString(), 256));
    }

    // 从任务书网络同步数据恢复任务详情面板背景图片
    public static void readNetData(Quest quest, RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            set(quest, null);
            return;
        }

        set(quest, Identifier.tryParse(buffer.readUtf(256)));
    }
}
