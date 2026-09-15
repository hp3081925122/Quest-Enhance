package com.quest_enhance.mixin;

import com.quest_enhance.client.config.QuestEnhanceClientConfig;
import com.quest_enhance.client.quest.EntityModelToastIcon;
import com.quest_enhance.client.quest.QuestEntityModel;
import com.quest_enhance.client.quest.QuestVideoData;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.client.gui.ToastQuestObject;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 将击杀任务完成通知的屏障占位图标替换为缓存的生物模型
@Mixin(value = ToastQuestObject.class, remap = false)
public abstract class ToastQuestObjectMixin {
    @Shadow
    @Final
    private QuestObject object;

    @Unique
    private EntityModelToastIcon quest_enhance$entity_model_icon;

    @Unique
    private Identifier quest_enhance$entity_model_icon_id;

    // 在 FTB 原完成通知绘制图标前提供生物模型图标
    @Inject(method = "getIcon", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$use_entity_model_icon(CallbackInfoReturnable<Icon<?>> callback_info) {
        Identifier entity_id = quest_enhance$get_entity_model();
        if (entity_id == null) {
            return;
        }

        if (this.quest_enhance$entity_model_icon == null
                || !entity_id.equals(this.quest_enhance$entity_model_icon_id)) {
            this.quest_enhance$entity_model_icon = new EntityModelToastIcon(entity_id, this.object.getIcon());
            this.quest_enhance$entity_model_icon_id = entity_id;
        }
        callback_info.setReturnValue(this.quest_enhance$entity_model_icon);
    }

    // 与任务书节点保持一致，优先使用手动模型，其次使用唯一击杀任务的目标实体
    @Unique
    private Identifier quest_enhance$get_entity_model() {
        if (!QuestEnhanceClientConfig.RENDER_KILL_TASK_ENTITY_MODELS.get()) {
            return null;
        }
        if (this.object instanceof KillTask kill_task) {
            return ((KillTaskAccessor) kill_task).quest_enhance$get_entity();
        }
        if (!(this.object instanceof Quest quest)) {
            return null;
        }

        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) quest).quest_enhance$get_raw_icon();
        Identifier explicit_model = QuestEntityModel.getEntityModel(raw_icon).orElse(null);
        if (explicit_model != null) {
            return explicit_model;
        }
        if (!raw_icon.isEmpty() && !QuestVideoData.isPlaceholder(raw_icon)) {
            return null;
        }

        KillTask kill_task = null;
        for (Object task : quest.getTasks()) {
            if (!(task instanceof KillTask candidate)) {
                continue;
            }
            if (kill_task != null) {
                return null;
            }
            kill_task = candidate;
        }
        return kill_task == null ? null : ((KillTaskAccessor) kill_task).quest_enhance$get_entity();
    }
}
