package com.quest_enhance.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;

public final class QuestEnhanceKubeJSPlugin extends KubeJSPlugin {
    // 注册任务书增强提供的 KubeJS 事件组
    @Override
    public void registerEvents() {
        QuestEnhanceKubeJSEvents.GROUP.register();
    }
}
