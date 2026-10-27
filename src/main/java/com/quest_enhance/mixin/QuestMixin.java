package com.quest_enhance.mixin;

import com.quest_enhance.QuestTaskRequirement;
import com.quest_enhance.common.QuestBackground;
import com.quest_enhance.common.QuestScrollPaging;
import com.quest_enhance.common.QuestViewBackground;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 将任务节点背景图片接入 FTB 任务节点的存档和同步流程
@Mixin(value = Quest.class, remap = false)
public abstract class QuestMixin {
    // 将任务节点背景图片写入任务书 JSON5 存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_quest_background(
            Json5Object data,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        QuestBackground.writeData((Quest) (Object) this, data);
        QuestViewBackground.writeData((Quest) (Object) this, data);
        QuestScrollPaging.writeData((Quest) (Object) this, data);
        QuestTaskRequirement.writeData((Quest) (Object) this, data);
    }

    // 从任务书 JSON5 存档恢复任务节点背景图片
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_quest_background(
            Json5Object data,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        QuestBackground.readData((Quest) (Object) this, data);
        QuestViewBackground.readData((Quest) (Object) this, data);
        QuestScrollPaging.readData((Quest) (Object) this, data);
        QuestTaskRequirement.readData((Quest) (Object) this, data);
    }

    // 将任务节点背景图片写入任务书网络同步数据
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_quest_background_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        QuestBackground.writeNetData((Quest) (Object) this, buffer);
        QuestViewBackground.writeNetData((Quest) (Object) this, buffer);
        QuestScrollPaging.writeNetData((Quest) (Object) this, buffer);
        QuestTaskRequirement.writeNetData((Quest) (Object) this, buffer);
    }

    // 从任务书网络同步数据恢复任务节点背景图片
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_quest_background_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        QuestBackground.readNetData((Quest) (Object) this, buffer);
        QuestViewBackground.readNetData((Quest) (Object) this, buffer);
        QuestScrollPaging.readNetData((Quest) (Object) this, buffer);
        QuestTaskRequirement.readNetData((Quest) (Object) this, buffer);
    }

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
