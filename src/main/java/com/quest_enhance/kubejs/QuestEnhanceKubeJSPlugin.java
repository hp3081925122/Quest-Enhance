package com.quest_enhance.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

public final class QuestEnhanceKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        QuestEnhanceKubeJSEvents.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("QuestUtils", QuestUtils.class);
    }
}
