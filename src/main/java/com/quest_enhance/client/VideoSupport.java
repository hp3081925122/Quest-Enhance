package com.quest_enhance.client;

import com.quest_enhance.QuestEnhance;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

public final class VideoSupport {
    private static final String PLAYER_SCREEN_CLASS = "com.quest_enhance.client.VideoPlayerScreen";

    private VideoSupport() {
    }

    // 只根据当前加载器中的模组列表判断视频后端是否可用
    public static boolean isAvailable() {
        return ModList.get().isLoaded("watermedia");
    }

    // 确认前置存在后再加载包含 WaterMedia 类型的播放器类
    public static void open(String video_path) {
        if (!isAvailable()) {
            showMessage(Component.translatable("quest_enhance.video.error.missing_dependency"));
            return;
        }

        try {
            Class.forName(PLAYER_SCREEN_CLASS)
                    .getMethod("open", String.class)
                    .invoke(null, video_path);
        } catch (ReflectiveOperationException | LinkageError exception) {
            QuestEnhance.LOGGER.error("Failed to load the optional WaterMedia video player", exception);
            showMessage(Component.translatable("quest_enhance.video.error.integration"));
        }
    }

    // 优先在客户端聊天栏中显示缺失或不兼容提示
    private static void showMessage(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }
}
