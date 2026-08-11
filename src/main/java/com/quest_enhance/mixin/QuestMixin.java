package com.quest_enhance.mixin;

import com.quest_enhance.common.QuestBackground;
import com.quest_enhance.common.QuestViewBackground;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 将任务节点背景图片接入 FTB 任务节点的存档和同步流程
@Mixin(value = Quest.class, remap = false)
public abstract class QuestMixin {
    // 将任务节点背景图片写入任务书存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_quest_background(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        QuestBackground.writeData((Quest) (Object) this, tag);
        QuestViewBackground.writeData((Quest) (Object) this, tag);
    }

    // 从任务书存档恢复任务节点背景图片
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_quest_background(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        QuestBackground.readData((Quest) (Object) this, tag);
        QuestViewBackground.readData((Quest) (Object) this, tag);
    }

    // 将任务节点背景图片写入任务书网络同步数据
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_quest_background_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        QuestBackground.writeNetData((Quest) (Object) this, buffer);
        QuestViewBackground.writeNetData((Quest) (Object) this, buffer);
    }

    // 从任务书网络同步数据恢复任务节点背景图片
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_quest_background_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        QuestBackground.readNetData((Quest) (Object) this, buffer);
        QuestViewBackground.readNetData((Quest) (Object) this, buffer);
    }
}
