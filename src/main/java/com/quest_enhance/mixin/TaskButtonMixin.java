package com.quest_enhance.mixin;

import com.quest_enhance.client.QuestEnhanceClientConfig;
import com.quest_enhance.client.KillTaskEntityPreview;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TaskButton.class, remap = false)
public abstract class TaskButtonMixin {
    @Shadow
    Task task;

    @Unique
    private KillTaskEntityPreview quest_enhance$entity_preview;

    // 在开关启用时用缓存的生物模型替代击杀任务刷怪蛋图标
    @Inject(method = "drawIcon", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$draw_kill_task_entity(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        if (!QuestEnhanceClientConfig.RENDER_KILL_TASK_ENTITY_MODELS.get()
                || !(this.task instanceof KillTask kill_task)) {
            return;
        }

        // 延迟创建预览缓存并按击杀任务当前实体注册名绘制
        if (this.quest_enhance$entity_preview == null) {
            this.quest_enhance$entity_preview = new KillTaskEntityPreview();
        }
        ResourceLocation entity_id = ((KillTaskAccessor) kill_task).quest_enhance$get_entity();
        if (this.quest_enhance$entity_preview.render(entity_id, graphics, x, y, width, height)) {
            callback_info.cancel();
        }
    }
}
