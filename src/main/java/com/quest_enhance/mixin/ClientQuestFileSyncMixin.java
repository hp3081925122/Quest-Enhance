package com.quest_enhance.mixin;

import com.quest_enhance.common.network.QuestEnhanceNetwork;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// FTB 任务书同步完成后，再通过 Quest Enhance 自己的数据包请求扩展字段
@Mixin(value = ClientQuestFile.class, remap = false)
public abstract class ClientQuestFileSyncMixin {
    @Inject(method = "syncFromServer", at = @At("TAIL"))
    private static void quest_enhance$request_extra_data(CallbackInfo callback_info) {
        QuestEnhanceNetwork.requestQuestExtraData();
    }
}
