package com.quest_enhance.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;

public final class QuestEnhanceKubeJSPlugin implements KubeJSPlugin {
    // 注册任务书增强提供的 KubeJS 事件组
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(QuestEnhanceKubeJSEvents.GROUP);
    }
}
