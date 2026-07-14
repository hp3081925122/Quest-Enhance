package com.ftb_paste_image.mixin;

import com.ftb_paste_image.client.QuestDescriptionWidthContext;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelMixin {
    @Shadow
    private BlankPanel panelText;

    // 在打开描述编辑器前保存节点介绍区域扣除边距后的实际宽度
    @Inject(method = "editDescription", at = @At("HEAD"))
    private void ftb_paste_image$capture_description_width(CallbackInfo callback_info) {
        QuestDescriptionWidthContext.capture(this.panelText.getWidth());
    }

    // 为节点介绍页的图片选择器绑定宽度，并只对新建图片默认开启自适应
    @Inject(method = "editImage", at = @At("HEAD"))
    private void ftb_paste_image$prepare_image_editor(
            int line,
            ImageComponent component,
            CallbackInfo callback_info
    ) {
        QuestDescriptionWidthContext.activate(this.panelText.getWidth());
        if (line == -1) {
            component.fit = true;
        }
    }
}
