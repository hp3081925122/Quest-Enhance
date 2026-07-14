package com.ftb_paste_image.client;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FtbPasteImageClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue RENDER_KILL_TASK_ENTITY_MODELS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        RENDER_KILL_TASK_ENTITY_MODELS = builder
                .comment("使用生物模型替代击杀实体任务的刷怪蛋图标")
                .define("render_kill_task_entity_models", true);
        SPEC = builder.build();
    }

    private FtbPasteImageClientConfig() {
    }
}
