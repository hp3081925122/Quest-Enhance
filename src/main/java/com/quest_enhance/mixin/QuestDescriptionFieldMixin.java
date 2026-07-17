package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.QuestDescriptionTable;
import com.quest_enhance.client.QuestDescriptionVideo;
import com.quest_enhance.client.VideoPlayerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel$QuestDescriptionField", remap = false)
public abstract class QuestDescriptionFieldMixin {
    // 优先处理描述中的本地视频点击事件，避免交给原生翻页逻辑
    @Inject(method = "handleCustomClickEvent", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_description_video(
            Style style,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (style == null || style.getClickEvent() == null) {
            return;
        }

        ClickEvent click_event = style.getClickEvent();
        String click_value = click_event.getValue();
        if (click_event.getAction() == ClickEvent.Action.CHANGE_PAGE
                && click_value.startsWith(QuestDescriptionTable.CLICK_PREFIX)) {
            callback_info.setReturnValue(true);
            return;
        }
        if (click_event.getAction() != ClickEvent.Action.CHANGE_PAGE
                || !click_value.startsWith(QuestDescriptionVideo.CLICK_PREFIX)) {
            return;
        }

        String encoded_path = click_value.substring(QuestDescriptionVideo.CLICK_PREFIX.length());
        QuestDescriptionVideo.decodePath(encoded_path).ifPresentOrElse(
                VideoPlayerScreen::open,
                () -> QuestEnhance.LOGGER.error(
                        "Failed to decode a quest description video path: {}",
                        encoded_path
                )
        );
        callback_info.setReturnValue(true);
    }
}
