package com.quest_enhance.mixin;

import com.quest_enhance.client.DescriptionComponentMenu;
import com.quest_enhance.client.QuestDescriptionWidthContext;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelMixin {
    @Shadow
    private BlankPanel panelText;

    @Shadow
    private Quest quest;

    // 在打开描述编辑器前保存节点介绍区域扣除边距后的实际宽度
    @Inject(method = "editDescription", at = @At("HEAD"))
    private void quest_enhance$capture_description_width(CallbackInfo callback_info) {
        QuestDescriptionWidthContext.capture(this.panelText.getWidth());
    }

    // 为节点介绍页的图片选择器绑定宽度，并只对新建图片默认开启自适应
    @Inject(method = "editImage", at = @At("HEAD"))
    private void quest_enhance$prepare_image_editor(
            int line,
            ImageComponent component,
            CallbackInfo callback_info
    ) {
        QuestDescriptionWidthContext.activate(this.panelText.getWidth());
        if (line == -1) {
            component.setFit(true);
        }
    }

    // 编辑独立快捷 JSON 组件时打开对应配置页，未知内容继续使用原版字符串编辑
    @Inject(method = "editDescLine0", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$edit_description_component(
            Widget clicked_widget,
            int line,
            @Nullable Object type,
            CallbackInfo callback_info
    ) {
        if (type != null || line < 0 || line >= this.quest.getRawDescription().size()) {
            return;
        }

        // 保存后只替换当前描述行，并沿用 FTB 的翻译列表更新流程
        Panel panel = (Panel) (Object) this;
        boolean handled = DescriptionComponentMenu.edit(
                panel,
                this.quest.getRawDescription().get(line),
                edited -> {
                    this.quest.modifyTranslatableListValue(
                            TranslationKey.QUEST_DESC,
                            description -> {
                                if (line < description.size()) {
                                    description.set(line, edited);
                                }
                            }
                    );
                    panel.refreshWidgets();
                }
        );
        if (handled) {
            callback_info.cancel();
        }
    }
}
