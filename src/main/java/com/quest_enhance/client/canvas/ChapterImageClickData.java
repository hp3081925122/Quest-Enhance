package com.quest_enhance.client.canvas;

import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.ImageClickAction;

public final class ChapterImageClickData {
    private ChapterImageClickData() {
    }

    public static String get(ChapterImage image) {
        return ((ChapterImageAccessor) (Object) image).quest_enhance$get_clickAction().actionData();
    }

    // 使用可序列化的自定义事件类型保存内部标记，避免 26.1.2 跳过 NONE 类型数据
    public static void set(ChapterImage image, String click_data) {
        ImageClickAction click_action = ((ChapterImageAccessor) (Object) image)
                .quest_enhance$get_clickAction()
                .withType(ImageClickAction.ActionType.CUSTOM_EVENT)
                .withData(click_data);
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_clickAction(click_action);
    }
}
