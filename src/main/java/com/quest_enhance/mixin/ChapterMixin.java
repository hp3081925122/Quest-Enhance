package com.quest_enhance.mixin;

import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.DecorativeDependencyLines;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Chapter.class, remap = false)
public abstract class ChapterMixin {
    // 将装饰线写入章节存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_decorative_lines(
            CompoundTag tag,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.writeData((Chapter) (Object) this, tag);
    }

    // 从章节存档恢复装饰线
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_decorative_lines(CompoundTag tag, CallbackInfo callback_info) {
        DecorativeDependencyLines.readData((Chapter) (Object) this, tag);
    }

    // 将装饰线追加到 FTB Quests 的编辑同步数据
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_decorative_lines_net(
            FriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.writeNetData((Chapter) (Object) this, buffer);
    }

    // 从 FTB Quests 的编辑同步数据恢复装饰线
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_decorative_lines_net(
            FriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.readNetData((Chapter) (Object) this, buffer);
    }

    // 删除任务时同步清理装饰线路径中的任务节点
    @Inject(method = "removeQuest", at = @At("HEAD"))
    private void quest_enhance$remove_deleted_quest_from_lines(
            Quest quest,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.removeNode(
                (Chapter) (Object) this,
                DecorativeDependencyLines.questNode(quest.getMovableID())
        );
    }

    // 删除辅助点时同步清理装饰线路径中的拐点节点
    @Inject(method = "removeImage", at = @At("HEAD"))
    private void quest_enhance$remove_deleted_anchor_from_lines(
            ChapterImage image,
            CallbackInfo callback_info
    ) {
        DecorativeAnchor.nodeKey(image).ifPresent(node_key ->
                DecorativeDependencyLines.removeNode((Chapter) (Object) this, node_key)
        );
    }
}
