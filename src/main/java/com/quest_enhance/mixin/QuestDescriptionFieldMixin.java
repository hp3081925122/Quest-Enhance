package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.integration.PonderIntegration;
import com.quest_enhance.client.description.QuestDescriptionTable;
import com.quest_enhance.client.description.QuestDescriptionVideo;
import com.quest_enhance.client.media.VideoSupport;
import com.quest_enhance.common.description.QuestDescriptionComponents;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel$QuestDescriptionField", remap = false)
public abstract class QuestDescriptionFieldMixin {
    // 兼容 FTB Quests 1.20.1 不支持“任务 ID/页码”格式的任务描述链接
    @Inject(method = "handleCustomClickEvent", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_quest_page(
            Style style,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (style == null || style.getClickEvent() == null) {
            return;
        }

        ClickEvent click_event = style.getClickEvent();
        if (click_event.getAction() != ClickEvent.Action.CHANGE_PAGE) {
            return;
        }

        String[] click_fields = click_event.getValue().split("/", 2);
        if (click_fields.length != 2) {
            return;
        }

        int page;
        try {
            page = Integer.parseInt(click_fields[1]);
        } catch (NumberFormatException exception) {
            return;
        }

        if (page < 1 || ClientQuestFile.INSTANCE == null) {
            return;
        }

        Quest quest = QuestObjectBase.parseHexId(click_fields[0])
                .map(ClientQuestFile.INSTANCE::getQuest)
                .orElse(null);
        if (quest == null) {
            return;
        }

        int page_count = Math.max(1, quest.buildDescriptionIndex().size());
        if (page > page_count) {
            callback_info.setReturnValue(true);
            return;
        }

        if (!(((TextField) (Object) this).getGui() instanceof QuestScreen quest_screen)) {
            return;
        }

        quest_screen.open(quest, false);
        ViewQuestPanel view_panel = quest_screen.viewQuestPanel;
        ((ViewQuestPanelAccessor) (Object) view_panel).quest_enhance$set_current_page(page - 1);
        view_panel.refreshWidgets();
        callback_info.setReturnValue(true);
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
