package com.quest_enhance.common;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 保存任务书章节侧边栏背景图片，并负责存档和网络同步数据的读写
public final class ChapterSidebarBackground {
    private static final String NBT_KEY = "quest_enhance_chapter_sidebar_background";
    private static final Map<BaseQuestFile, ResourceLocation> BACKGROUNDS = new WeakHashMap<>();

    private ChapterSidebarBackground() {
    }

    // 读取任务书当前设置的章节侧边栏背景图片
    public static Optional<ResourceLocation> get(BaseQuestFile file) {
        return Optional.ofNullable(BACKGROUNDS.get(file));
    }

    // 更新任务书章节侧边栏背景图片，空值表示恢复默认背景
    public static void set(BaseQuestFile file, ResourceLocation resource_location) {
        if (resource_location == null) {
            BACKGROUNDS.remove(file);
        } else {
            BACKGROUNDS.put(file, resource_location);
        }
    }

    // 将章节侧边栏背景图片写入任务书存档
    public static void writeData(BaseQuestFile file, CompoundTag tag) {
        get(file).ifPresent(resource_location -> tag.putString(NBT_KEY, resource_location.toString()));
    }

    // 从任务书存档恢复章节侧边栏背景图片
    public static void readData(BaseQuestFile file, CompoundTag tag) {
        set(file, null);
        if (!tag.contains(NBT_KEY, 8)) {
            return;
        }

        set(file, ResourceLocation.tryParse(tag.getString(NBT_KEY)));
    }


}
