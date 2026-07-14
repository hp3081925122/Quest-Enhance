package com.ftb_paste_image.mixin;

import com.ftb_paste_image.client.FtbPasteImageClientConfig;
import com.ftb_paste_image.client.KillTaskEntityPreview;
import com.ftb_paste_image.client.QuestEntityModel;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestButton.class, remap = false)
public abstract class QuestButtonMixin {
    @Shadow
    @Final
    Quest quest;

    @Unique
    private KillTaskEntityPreview ftb_paste_image$entity_preview;

    // 阻止原图标绘制，为手动模型或自动击杀任务模型保留节点中央区域
    @Redirect(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/icon/Icon;isEmpty()Z",
                    ordinal = 0
            )
    )
    private boolean ftb_paste_image$hide_replaced_icon(Icon icon) {
        return this.ftb_paste_image$get_entity_model() != null || icon.isEmpty();
    }

    // 在节点背景之后、状态覆盖图标之前绘制实体模型
    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/GuiHelper;setupDrawing()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void ftb_paste_image$draw_quest_entity_model(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        ResourceLocation entity_id = this.ftb_paste_image$get_entity_model();
        if (entity_id == null) {
            return;
        }

        // 按 FTB 原节点图标缩放规则计算模型占用区域
        if (this.ftb_paste_image$entity_preview == null) {
            this.ftb_paste_image$entity_preview = new KillTaskEntityPreview();
        }
        int model_size = Math.max(1, Math.min(
                Math.min(width, height),
                Math.round(width * 0.6666667F * (float) this.quest.getIconScale())
        ));
        int model_x = x + (width - model_size) / 2;
        int model_y = y + (height - model_size) / 2;
        this.ftb_paste_image$entity_preview.render(
                entity_id,
                graphics,
                model_x,
                model_y,
                model_size,
                model_size
        );
    }

    // 手动模型优先，普通自定义图标次之，最后使用单个击杀任务的目标实体
    @Unique
    private ResourceLocation ftb_paste_image$get_entity_model() {
        if (!FtbPasteImageClientConfig.RENDER_KILL_TASK_ENTITY_MODELS.get()) {
            return null;
        }

        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) this.quest).ftb_paste_image$get_raw_icon();
        ResourceLocation explicit_model = QuestEntityModel.getEntityModel(raw_icon).orElse(null);
        if (explicit_model != null) {
            return explicit_model;
        }
        if (!raw_icon.isEmpty() || this.quest.getTasks().size() != 1) {
            return null;
        }

        return this.quest.getTasks().iterator().next() instanceof KillTask kill_task
                ? ((KillTaskAccessor) kill_task).ftb_paste_image$get_entity()
                : null;
    }
}
