package com.quest_enhance.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public final class QuestEnhanceKubeJSPlugin implements KubeJSPlugin {
    // 注册任务书增强提供的 KubeJS 事件组
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(QuestEnhanceKubeJSEvents.GROUP);
    }

    // 向 KubeJS 脚本暴露跨版本兼容的任务书工具类
    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("QuestUtils", QuestUtils.class);
    }
}
