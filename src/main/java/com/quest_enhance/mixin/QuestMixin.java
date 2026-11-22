package com.quest_enhance.mixin;

import com.quest_enhance.QuestBackground;
import com.quest_enhance.QuestScrollPaging;
import com.quest_enhance.QuestTaskRequirement;
import com.quest_enhance.QuestViewBackground;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 将任务节点背景图片接入 FTB 任务节点的存档和同步流程
@Mixin(value = Quest.class, remap = false)
public abstract class QuestMixin {
    // 将任务节点背景图片写入任务书存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_quest_background(CompoundTag tag, CallbackInfo callback_info) {
        QuestBackground.writeData((Quest) (Object) this, tag);
        QuestViewBackground.writeData((Quest) (Object) this, tag);
        QuestScrollPaging.writeData((Quest) (Object) this, tag);
        QuestTaskRequirement.writeData((Quest) (Object) this, tag);
    }

    // 从任务书存档恢复任务节点背景图片
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_quest_background(CompoundTag tag, CallbackInfo callback_info) {
        QuestBackground.readData((Quest) (Object) this, tag);
        QuestViewBackground.readData((Quest) (Object) this, tag);
        QuestScrollPaging.readData((Quest) (Object) this, tag);
        QuestTaskRequirement.readData((Quest) (Object) this, tag);
    }

    // 网络同步改由 QuestExtraDataSync 独立数据包完成，不再向 FTB 的同步流追加字节

    @Inject(method = "isCompletedRaw", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$check_minimum_tasks(TeamData teamData, CallbackInfoReturnable<Boolean> callbackInfo) {
        Quest quest = (Quest) (Object) this;
        if (QuestTaskRequirement.getRequiredTaskCount(quest) > 0) {
            callbackInfo.setReturnValue(QuestTaskRequirement.isCompleted(quest, teamData));
        }
    }

    @Inject(method = "getRelativeProgressFromChildren", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$minimum_task_progress(TeamData teamData, CallbackInfoReturnable<Integer> callbackInfo) {
        Quest quest = (Quest) (Object) this;
        if (QuestTaskRequirement.getRequiredTaskCount(quest) > 0) {
            callbackInfo.setReturnValue(QuestTaskRequirement.getRelativeProgress(quest, teamData));
        }
    }

    @Inject(method = "allTasksCompleted", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$minimum_task_progress_ready(TeamData teamData, CallbackInfoReturnable<Boolean> callbackInfo) {
        Quest quest = (Quest) (Object) this;
        if (QuestTaskRequirement.getRequiredTaskCount(quest) > 0) {
            callbackInfo.setReturnValue(QuestTaskRequirement.hasEnoughTaskProgress(quest, teamData));
        }
    }
}
