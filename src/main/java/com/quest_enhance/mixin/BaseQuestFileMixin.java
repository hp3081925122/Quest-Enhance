package com.quest_enhance.mixin;

import com.quest_enhance.ChapterSidebarBackground;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 将章节侧边栏背景图片接入 FTB 任务书文件的存档和同步流程
@Mixin(value = BaseQuestFile.class, remap = false)
public abstract class BaseQuestFileMixin {
    // 将章节侧边栏背景图片写入任务书文件存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_sidebar_background(CompoundTag tag, CallbackInfo callback_info) {
        ChapterSidebarBackground.writeData((BaseQuestFile) (Object) this, tag);
    }

    // 从任务书文件存档恢复章节侧边栏背景图片
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_sidebar_background(CompoundTag tag, CallbackInfo callback_info) {
        ChapterSidebarBackground.readData((BaseQuestFile) (Object) this, tag);
    }

    // 将章节侧边栏背景图片写入任务书网络同步数据
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_sidebar_background_net(FriendlyByteBuf buffer, CallbackInfo callback_info) {
        ChapterSidebarBackground.writeNetData((BaseQuestFile) (Object) this, buffer);
    }

    // 从任务书网络同步数据恢复章节侧边栏背景图片
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_sidebar_background_net(FriendlyByteBuf buffer, CallbackInfo callback_info) {
        ChapterSidebarBackground.readNetData((BaseQuestFile) (Object) this, buffer);
    }
}
