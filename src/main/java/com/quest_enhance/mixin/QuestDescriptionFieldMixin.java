package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.integration.PonderIntegration;
import com.quest_enhance.client.description.QuestDescriptionTable;
import com.quest_enhance.client.description.QuestDescriptionVideo;
import com.quest_enhance.client.media.VideoSupport;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel$QuestDescriptionField", remap = false)
public abstract class QuestDescriptionFieldMixin {
    // 表格描述行使用自定义网格渲染替代普通文字绘制
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$draw_table(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        Component raw_text = ((TextFieldAccessor) (Object) this).quest_enhance$get_raw_text();
        QuestDescriptionTable.find(raw_text).ifPresent(table -> {
            QuestDescriptionTable.draw(graphics, theme, x, y, width, table);
            callback_info.cancel();
        });
    }

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
                VideoSupport::open,
                () -> QuestEnhance.LOGGER.error(
                        "Failed to decode a quest description video path: {}",
                        encoded_path
                )
        );
        callback_info.setReturnValue(true);
    }

    // 优先处理思索组件，避免交给 FTB Quests 的 CHANGE_PAGE 逻辑。
    @Inject(method = "handleCustomClickEvent", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_ponder(
            Style style,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (style == null || style.getClickEvent() == null) {
            return;
        }
        ClickEvent click_event = style.getClickEvent();
        String click_value = click_event.getValue();
        if (click_event.getAction() != ClickEvent.Action.CHANGE_PAGE
                || !click_value.startsWith(QuestDescriptionComponents.PONDER_CLICK_PREFIX)) {
            return;
        }
        ResourceLocation item_id = ResourceLocation.tryParse(
                click_value.substring(QuestDescriptionComponents.PONDER_CLICK_PREFIX.length())
        );
        if (item_id != null) {
            PonderIntegration.open(item_id);
        }
        callback_info.setReturnValue(true);
    }
}
