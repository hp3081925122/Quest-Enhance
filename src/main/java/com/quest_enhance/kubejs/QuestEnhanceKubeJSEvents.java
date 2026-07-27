package com.quest_enhance.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public final class QuestEnhanceKubeJSEvents {
    // 定义仅在客户端脚本中可监听的画布文字点击事件
    public static final EventGroup GROUP = EventGroup.of("QuestEnhanceEvents");
    public static final EventHandler CLICK = GROUP.client("click", () -> QuestEnhanceTextClickEvent.class);

    private QuestEnhanceKubeJSEvents() {
    }
}
