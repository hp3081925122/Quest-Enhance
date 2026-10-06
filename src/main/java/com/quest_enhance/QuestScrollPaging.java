package com.quest_enhance;

import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Map;
import java.util.WeakHashMap;

// 保存任务节点是否关闭滚轮翻页，并负责任务文件和网络同步数据的读写
public final class QuestScrollPaging {
    private static final String NBT_KEY = "quest_enhance_disable_scroll_paging";
    private static final Map<Quest, Boolean> DISABLED = new WeakHashMap<>();

    private QuestScrollPaging() {
    }

    // 查询任务节点是否关闭滚轮翻页
    public static boolean isDisabled(Quest quest) {
        return Boolean.TRUE.equals(DISABLED.get(quest));
    }

    // 更新任务节点的滚轮翻页开关
    public static void setDisabled(Quest quest, boolean disabled) {
        if (disabled) {
            DISABLED.put(quest, true);
        } else {
            DISABLED.remove(quest);
        }
    }

    // 将任务节点开关写入任务书存档
    public static void writeData(Quest quest, CompoundTag tag) {
        tag.putBoolean(NBT_KEY, isDisabled(quest));
    }

    // 从任务书存档恢复任务节点开关
    public static void readData(Quest quest, CompoundTag tag) {
        setDisabled(quest, tag.getBoolean(NBT_KEY));
    }

    // 将任务节点开关写入任务书网络同步数据
    public static void writeNetData(Quest quest, FriendlyByteBuf buffer) {
        buffer.writeBoolean(isDisabled(quest));
    }

    // 从任务书网络同步数据恢复任务节点开关
    public static void readNetData(Quest quest, FriendlyByteBuf buffer) {
        setDisabled(quest, buffer.readBoolean());
    }
}
