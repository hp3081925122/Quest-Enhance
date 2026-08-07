package com.quest_enhance;

import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

// 保存章节背景图片的资源标识，并负责章节存档与编辑同步数据的读写
public final class ChapterBackground {
    private static final String NBT_KEY = "quest_enhance_chapter_background";
    private static final Map<Chapter, ResourceLocation> BACKGROUNDS = new WeakHashMap<>();

    private ChapterBackground() {
    }

    // 读取章节当前设置的背景图片资源
    public static Optional<ResourceLocation> get(Chapter chapter) {
        return Optional.ofNullable(BACKGROUNDS.get(chapter));
    }

    // 更新章节背景图片，传入空值时恢复默认背景
    public static void set(Chapter chapter, ResourceLocation resource_location) {
        if (resource_location == null) {
            BACKGROUNDS.remove(chapter);
        } else {
            BACKGROUNDS.put(chapter, resource_location);
        }
    }

    // 将章节背景图片写入任务文件存档
    public static void writeData(Chapter chapter, CompoundTag tag) {
        get(chapter).ifPresent(resource_location -> tag.putString(NBT_KEY, resource_location.toString()));
    }

    // 从任务文件存档恢复章节背景图片
    public static void readData(Chapter chapter, CompoundTag tag) {
        set(chapter, null);
        if (!tag.contains(NBT_KEY, 8)) {
            return;
        }

        ResourceLocation resource_location = ResourceLocation.tryParse(tag.getString(NBT_KEY));
        set(chapter, resource_location);
    }

    // 将章节背景图片追加到 FTB Quests 的编辑同步数据
    public static void writeNetData(Chapter chapter, FriendlyByteBuf buffer) {
        Optional<ResourceLocation> resource_location = get(chapter);
        buffer.writeBoolean(resource_location.isPresent());
        resource_location.ifPresent(value -> buffer.writeUtf(value.toString(), 256));
    }

    // 从 FTB Quests 的编辑同步数据恢复章节背景图片
    public static void readNetData(Chapter chapter, FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            set(chapter, null);
            return;
        }

        ResourceLocation resource_location = ResourceLocation.tryParse(buffer.readUtf(256));
        set(chapter, resource_location);
    }
}
