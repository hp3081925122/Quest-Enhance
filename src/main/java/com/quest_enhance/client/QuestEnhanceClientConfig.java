package com.quest_enhance.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class QuestEnhanceClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue RENDER_KILL_TASK_ENTITY_MODELS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        RENDER_KILL_TASK_ENTITY_MODELS = builder
                .comment("使用生物模型替代击杀实体任务的刷怪蛋图标")
                .define("render_kill_task_entity_models", true);
        SPEC = builder.build();
    }

    private QuestEnhanceClientConfig() {
    }
}
