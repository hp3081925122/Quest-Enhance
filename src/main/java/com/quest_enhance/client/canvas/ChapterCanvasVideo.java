package com.quest_enhance.client.canvas;

import com.quest_enhance.common.canvas.ChapterCanvasData;
import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;

import java.util.Optional;

public final class ChapterCanvasVideo {
    private ChapterCanvasVideo() {
    }

    // 判断章节图片是否是本模组保存的视频背景并读取相对路径
    public static Optional<VideoData> getVideoData(ChapterImage image) {
        return ChapterCanvasData.getVideo(image).map(VideoData::new);
    }

    // 修改视频背景时继续复用 FTB 原生字段保存和同步
    public static void setVideo(ChapterImage image, String video_path) {
        String click = ChapterCanvasData.videoClick(video_path).orElse("");
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_click(click);
    }

    // 创建一个可缩放的十六比九章节视频背景对象
    public static ChapterImage create(Chapter chapter, String video_path, double x, double y) {
        ChapterImage image = new ChapterImage(chapter)
                .setPosition(x, y)
                .setImage(Color4I.DARK_GRAY);
        ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
        accessor.quest_enhance$set_width(4.0);
        accessor.quest_enhance$set_height(2.25);
        setVideo(image, video_path);
        return image;
    }

    public record VideoData(String path) {
    }
}
