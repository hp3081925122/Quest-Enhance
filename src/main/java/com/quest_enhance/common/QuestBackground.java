package com.quest_enhance.common;

import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 保存普通任务节点背景图片，并负责任务节点存档和网络同步数据的读写
public final class QuestBackground {
    private static final String NBT_KEY = "quest_enhance_quest_background";
    private static final Map<Quest, ResourceLocation> BACKGROUNDS = new WeakHashMap<>();

    private QuestBackground() {
    }

    // 读取任务节点当前设置的背景图片
    public static Optional<ResourceLocation> get(Quest quest) {
        return Optional.ofNullable(BACKGROUNDS.get(quest));
    }

    // 更新任务节点背景图片，空值表示恢复默认节点背景
    public static void set(Quest quest, ResourceLocation resource_location) {
        if (resource_location == null) {
            BACKGROUNDS.remove(quest);
        } else {
            BACKGROUNDS.put(quest, resource_location);
        }
    }

    // 将任务节点背景图片写入任务书存档
    public static void writeData(Quest quest, CompoundTag tag) {
        get(quest).ifPresent(resource_location -> tag.putString(NBT_KEY, resource_location.toString()));
    }

    // 从任务书存档恢复任务节点背景图片
    public static void readData(Quest quest, CompoundTag tag) {
        set(quest, null);
        if (!tag.contains(NBT_KEY, 8)) {
            return;
        }

        set(quest, ResourceLocation.tryParse(tag.getString(NBT_KEY)));
    }

    // 将任务节点背景图片写入任务书网络同步数据
    public static void writeNetData(Quest quest, RegistryFriendlyByteBuf buffer) {
        Optional<ResourceLocation> resource_location = get(quest);
        buffer.writeBoolean(resource_location.isPresent());
        resource_location.ifPresent(value -> buffer.writeUtf(value.toString(), 256));
    }

    // 从任务书网络同步数据恢复任务节点背景图片
    public static void readNetData(Quest quest, RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            set(quest, null);
            return;
        }

        set(quest, ResourceLocation.tryParse(buffer.readUtf(256)));
    }
}
