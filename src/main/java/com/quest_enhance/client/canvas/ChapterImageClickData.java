package com.quest_enhance.client.canvas;

import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftbquests.quest.ChapterImage;

public final class ChapterImageClickData {
    private ChapterImageClickData() {
    }

    // 读取新版 FTB 点击动作中保存的特殊画布数据
    public static String get(ChapterImage image) {
        return ((ChapterImageAccessor) (Object) image).quest_enhance$get_click();
    }

    // 以无操作点击动作保存特殊画布数据，避免原生点击执行额外行为
    public static void set(ChapterImage image, String click_data) {
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_click(click_data);
    }
}
