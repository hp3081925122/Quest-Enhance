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

    public static void set(ChapterImage image, String click_data) {
        ImageClickAction click_action = ((ChapterImageAccessor) (Object) image)
                .quest_enhance$get_clickAction()
                .withType(ImageClickAction.ActionType.NONE)
                .withData(click_data);
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_clickAction(click_action);
    }
}
