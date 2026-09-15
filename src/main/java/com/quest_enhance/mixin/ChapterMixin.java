package com.quest_enhance.mixin;

import com.quest_enhance.ChapterPrerequisites;
import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.DecorativeDependencyLines;
import com.quest_enhance.HiddenDependencyLines;
import com.quest_enhance.common.ChapterBackground;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Chapter.class, remap = false)
public abstract class ChapterMixin {
    // 将装饰线写入章节存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_decorative_lines(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.writeData((Chapter) (Object) this, tag);
        HiddenDependencyLines.writeData((Chapter) (Object) this, tag);
        ChapterBackground.writeData((Chapter) (Object) this, tag);
    }

    // 从章节存档恢复装饰线
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_decorative_lines(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.readData((Chapter) (Object) this, tag);
        HiddenDependencyLines.readData((Chapter) (Object) this, tag);
        ChapterBackground.readData((Chapter) (Object) this, tag);
    }

    // 将装饰线追加到 FTB Quests 的编辑同步数据
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_decorative_lines_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.writeNetData((Chapter) (Object) this, buffer);
        HiddenDependencyLines.writeNetData((Chapter) (Object) this, buffer);
        ChapterBackground.writeNetData((Chapter) (Object) this, buffer);
    }

    // 从 FTB Quests 的编辑同步数据恢复装饰线
    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_decorative_lines_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.readNetData((Chapter) (Object) this, buffer);
        HiddenDependencyLines.readNetData((Chapter) (Object) this, buffer);
        ChapterBackground.readNetData((Chapter) (Object) this, buffer);
        ChapterPrerequisites.readNetData((Chapter) (Object) this, buffer);
    }

    // 将章节前置写入任务文件存档
    @Inject(method = "writeData", at = @At("RETURN"))
    private void quest_enhance$write_chapter_prerequisites(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        ChapterPrerequisites.writeData((Chapter) (Object) this, tag);
    }

    // 从任务文件存档恢复章节前置
    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_chapter_prerequisites(
            CompoundTag tag,
            HolderLookup.Provider provider,
            CallbackInfo callback_info
    ) {
        ChapterPrerequisites.readData((Chapter) (Object) this, tag);
    }

    // 将章节前置写入 FTB Quests 的编辑同步数据
    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_chapter_prerequisites_net(
            RegistryFriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        ChapterPrerequisites.writeNetData((Chapter) (Object) this, buffer);
    }

    // 在普通玩家界面中要求章节任务节点前置全部完成，并允许已解锁的空章节显示
    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true)
    private void quest_enhance$apply_chapter_prerequisites(
            TeamData team_data,
            CallbackInfoReturnable<Boolean> return_info
    ) {
        Chapter chapter = (Chapter) (Object) this;
        if (chapter.getQuestFile().canEdit()
                || ChapterPrerequisites.getPrerequisiteIds(chapter).isEmpty()) {
            return;
        }

        if (!ChapterPrerequisites.arePrerequisitesCompleted(chapter, team_data)) {
            return_info.setReturnValue(false);
            return;
        }

        // FTB Quests 会把没有任务和链接任务的章节判定为不可见，这里只恢复已解锁的普通空章节。
        if (!return_info.getReturnValueZ()
                && !chapter.isAlwaysInvisible()
                && chapter.getQuests().isEmpty()
                && chapter.getQuestLinks().isEmpty()) {
            return_info.setReturnValue(true);
        }
    }

    // 删除章节时同步移除其他章节对它的前置引用
    @Inject(method = "deleteSelf", at = @At("HEAD"))
    private void quest_enhance$remove_chapter_prerequisites(CallbackInfo callback_info) {
        ChapterPrerequisites.removeChapter((Chapter) (Object) this);
    }

    // 删除任务时同步清理装饰线路径中的任务节点
    @Inject(method = "removeQuest", at = @At("HEAD"))
    private void quest_enhance$remove_deleted_quest_from_lines(
            Quest quest,
            CallbackInfo callback_info
    ) {
        ChapterPrerequisites.removeQuest(quest);
        DecorativeDependencyLines.removeNode(
                (Chapter) (Object) this,
                DecorativeDependencyLines.questNode(quest.getMovableID())
        );
        HiddenDependencyLines.removeNode(
                (Chapter) (Object) this,
                HiddenDependencyLines.nodeKey(quest)
        );
    }

    // 删除链接任务时同步清理装饰线路径中的链接节点
    @Inject(method = "removeQuestLink", at = @At("HEAD"))
    private void quest_enhance$remove_deleted_quest_link_from_lines(
            QuestLink link,
            CallbackInfo callback_info
    ) {
        DecorativeDependencyLines.removeNode(
                (Chapter) (Object) this,
                DecorativeDependencyLines.questLinkNode(link.getMovableID())
        );
        HiddenDependencyLines.removeNode(
                (Chapter) (Object) this,
                HiddenDependencyLines.nodeKey(link)
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
