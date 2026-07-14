package com.ftb_paste_image.mixin;

import com.ftb_paste_image.client.FtbPasteImageClientConfig;
import com.ftb_paste_image.client.KillTaskEntityPreview;
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
    private KillTaskEntityPreview ftb_paste_image$entity_preview;

    // 在开关启用时用缓存的生物模型替代击杀任务刷怪蛋图标
    @Inject(method = "drawIcon", at = @At("HEAD"), cancellable = true)
    private void ftb_paste_image$draw_kill_task_entity(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        if (!FtbPasteImageClientConfig.RENDER_KILL_TASK_ENTITY_MODELS.get()
                || !(this.task instanceof KillTask kill_task)) {
            return;
        }

        // 延迟创建预览缓存并按击杀任务当前实体注册名绘制
        if (this.ftb_paste_image$entity_preview == null) {
            this.ftb_paste_image$entity_preview = new KillTaskEntityPreview();
        }
        ResourceLocation entity_id = ((KillTaskAccessor) kill_task).ftb_paste_image$get_entity();
        if (this.ftb_paste_image$entity_preview.render(entity_id, graphics, x, y, width, height)) {
            callback_info.cancel();
        }
    }
}
