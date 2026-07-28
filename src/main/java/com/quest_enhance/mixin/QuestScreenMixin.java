package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.ChapterClipboardImage;
import com.quest_enhance.client.QuestEnhanceClipboardEntry;
import com.mojang.datafixers.util.Pair;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.Key;
import dev.ftb.mods.ftbquests.client.gui.CustomToast;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPositionableButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.CopyChapterImageMessage;
import dev.ftb.mods.ftbquests.net.CopyQuestMessage;
import dev.ftb.mods.ftbquests.net.CreateObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestLink;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = QuestScreen.class, remap = false)
public abstract class QuestScreenMixin {
    @Unique
    private static final String QUEST_ENHANCE_MULTI_CLIPBOARD = "<quest-enhance-multi>";

    @Unique
    private static List<QuestEnhanceClipboardEntry> quest_enhance$multi_clipboard = List.of();

    @Shadow
    @Final
    private QuestPanel questPanel;

    // 将多选任务和图片保存为相对左上角的坐标组，替换原生“不支持复制多个对象”分支
    @Inject(method = "copyObjectsToClipboard", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$copy_multiple_objects(CallbackInfoReturnable<Boolean> callback_info) {
        QuestScreenAccessor accessor = (QuestScreenAccessor) this;
        List<Movable> selected_objects = accessor.quest_enhance$get_selected_objects().stream().distinct().toList();
        if (selected_objects.size() <= 1) {
            Movable copied_object = selected_objects.isEmpty() ? null : selected_objects.get(0);
            if (copied_object == null) {
                for (Widget widget : this.questPanel.getWidgets()) {
                    if (widget instanceof QuestPositionableButton positionable && widget.isMouseOver()) {
                        copied_object = positionable.moveAndDeleteFocus();
                        break;
                    }
                }
            }

            // 链接节点必须复制其关联的真实任务，FTB 原生粘贴逻辑只接受任务 ID
            if (copied_object instanceof QuestLink link) {
                Quest quest = link.getRelatedQuest();
                if (quest == null) {
                    QuestScreen.displayError(Component.translatable("quest_enhance.link_copy.missing"));
                    callback_info.setReturnValue(true);
                    return;
                }

                quest.copyToClipboard();
                QuestEnhance.LOGGER.debug("Resolved linked quest copy: quest={}", quest.getId());
                Minecraft.getInstance().getToasts().addToast(new CustomToast(
                        Component.translatable("ftbquests.quest.copied"),
                        Icons.INFO,
                        Component.literal(quest.getTitle().getString())
                ));
                callback_info.setReturnValue(true);
                return;
            }
            quest_enhance$multi_clipboard = List.of();
            return;
        }

        // 当前批量协议只复用 FTB 已有的任务和章节图片复制消息
        if (selected_objects.stream().anyMatch(object -> !(object instanceof Quest)
                && !(object instanceof ChapterImage))) {
            quest_enhance$multi_clipboard = List.of();
            QuestScreen.displayError(Component.translatable("quest_enhance.multi_copy.unsupported"));
            callback_info.setReturnValue(true);
            return;
        }

        // 以选区最左上对象坐标为粘贴锚点，保证任务和图片之间的相对位置不变
        double anchor_x = selected_objects.stream().mapToDouble(Movable::getX).min().orElse(0.0D);
        double anchor_y = selected_objects.stream().mapToDouble(Movable::getY).min().orElse(0.0D);
        List<QuestEnhanceClipboardEntry> copied_objects = new ArrayList<>(selected_objects.size());
        for (Movable object : selected_objects) {
            copied_objects.add(new QuestEnhanceClipboardEntry(
                    object,
                    object.getX() - anchor_x,
                    object.getY() - anchor_y
            ));
        }
        quest_enhance$multi_clipboard = List.copyOf(copied_objects);
        Widget.setClipboardString(QUEST_ENHANCE_MULTI_CLIPBOARD);
        QuestEnhance.LOGGER.debug(
                "Copied multi-selection: count={}, anchorX={}, anchorY={}",
                copied_objects.size(),
                anchor_x,
                anchor_y
        );
        Minecraft.getInstance().getToasts().addToast(new CustomToast(
                Component.translatable("ftbquests.quest.copied"),
                Icons.INFO,
                Component.translatable("quest_enhance.multi_copy.count", copied_objects.size())
        ));
        callback_info.setReturnValue(true);
    }

    // 在 Ctrl+V 时将整组对象粘贴到鼠标网格位置，并沿用原生 Shift 控制依赖复制行为
    @Inject(method = "pasteSelectedQuest", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$paste_multiple_objects(
            boolean copy_dependencies,
            Chapter target_chapter,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (quest_enhance$multi_clipboard.size() <= 1
                || !QUEST_ENHANCE_MULTI_CLIPBOARD.equals(Widget.getClipboardString())) {
            return;
        }

        if (target_chapter == null) {
            callback_info.setReturnValue(false);
            return;
        }

        // 为每个条目发送 FTB 原生复制消息，由服务端继续负责完整任务和图片数据复制
        QuestScreenAccessor accessor = (QuestScreenAccessor) this;
        Pair<Double, Double> target = accessor.quest_enhance$invoke_get_snapped_xy();
        QuestEnhance.LOGGER.debug(
                "Pasting multi-selection: count={}, chapter={}, targetX={}, targetY={}, copyDependencies={}",
                quest_enhance$multi_clipboard.size(),
                target_chapter.getId(),
                target.getFirst(),
                target.getSecond(),
                copy_dependencies
        );
        for (QuestEnhanceClipboardEntry entry : quest_enhance$multi_clipboard) {
            double x = target.getFirst() + entry.offset_x();
            double y = target.getSecond() + entry.offset_y();
            if (entry.object() instanceof Quest quest) {
                NetworkManager.sendToServer(new CopyQuestMessage(
                        quest.getId(),
                        target_chapter.getId(),
                        x,
                        y,
                        copy_dependencies
                ));
            } else if (entry.object() instanceof ChapterImage image) {
                NetworkManager.sendToServer(new CopyChapterImageMessage(image.getId(), target_chapter.getId(), x, y));
            }
        }
        callback_info.setReturnValue(true);
    }

    // 在画布内优先粘贴剪贴板图片，并保留原生文字和任务粘贴行为
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$paste_clipboard_image(
            Key key,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (!key.paste()) {
            return;
        }

        QuestScreenAccessor accessor = (QuestScreenAccessor) this;
        Chapter chapter = accessor.quest_enhance$get_selected_chapter();
        if (chapter == null || !accessor.quest_enhance$get_file().canEdit()) {
            return;
        }

        // 仅在剪贴板确实含图片时截获 Ctrl+V，其余情况继续交给 FTB 原生粘贴处理
        Pair<Double, Double> target = accessor.quest_enhance$invoke_get_snapped_xy();
        ChapterImage image = ChapterClipboardImage.paste(
                chapter,
                target.getFirst(),
                target.getSecond(),
                ((QuestScreen) (Object) this).getQuestButtonSize()
        );
        if (image == null) {
            return;
        }

        NetworkManager.sendToServer(CreateObjectMessage.requestCreation(image));
        ((QuestScreen) (Object) this).refreshQuestPanel();
        callback_info.setReturnValue(true);
    }

}
