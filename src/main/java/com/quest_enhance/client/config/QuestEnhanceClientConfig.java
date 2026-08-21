package com.quest_enhance.client.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class QuestEnhanceClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue RENDER_KILL_TASK_ENTITY_MODELS;
    public static final ForgeConfigSpec.BooleanValue OPEN_ADVANCEMENT_TASKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        RENDER_KILL_TASK_ENTITY_MODELS = builder
                .comment("使用生物模型替代击杀实体任务的刷怪蛋图标")
                .define("render_kill_task_entity_models", true);
        OPEN_ADVANCEMENT_TASKS = builder
                .comment("左键点击进度任务时打开对应的原版进度界面")
                .define("open_advancement_tasks", true);
        SPEC = builder.build();
    }

    private QuestEnhanceClientConfig() {
    }
}
