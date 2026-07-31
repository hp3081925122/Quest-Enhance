package com.quest_enhance.mixin;

import com.quest_enhance.client.description.DescriptionComponentMenu;
import com.quest_enhance.client.description.MultilineTextEditorAccess;
import com.quest_enhance.QuestEnhance;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.client.gui.widget.Panel;
import dev.ftb.mods.ftblibrary.client.gui.widget.SimpleTextButton;
import dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen$ToolbarPanel", remap = false)
public abstract class MultilineTextEditorToolbarMixin {
    @Unique
    private MultilineTextEditorScreen quest_enhance$editor;

    @Unique
    private SimpleTextButton quest_enhance$component_button;

    // 保存工具栏所属编辑器，避免访问编译器生成的外部类字段
    @Inject(method = "<init>", at = @At("TAIL"))
    private void quest_enhance$capture_editor(
            MultilineTextEditorScreen outerPanel,
            Panel parent,
            CallbackInfo callback_info
    ) {
        this.quest_enhance$editor = outerPanel;
    }

    // 在描述编辑器工具栏中加入统一组件按钮
    @Inject(method = "addWidgets", at = @At("TAIL"))
    private void quest_enhance$add_component_button(CallbackInfo callback_info) {
        Panel panel = (Panel) (Object) this;
        this.quest_enhance$component_button = SimpleTextButton.create(
                panel,
                Component.translatable("quest_enhance.description_component.add"),
                Icons.ADD,
                mouse_button -> DescriptionComponentMenu.open(
                        panel,
                        (MultilineTextEditorAccess) this.quest_enhance$editor
                ),
                Component.translatable("quest_enhance.description_component.add")
        );
        panel.add(this.quest_enhance$component_button);
        QuestEnhance.LOGGER.debug("Added description component toolbar button");
    }

    // 将组件按钮放在原生图片和格式转换按钮之后
    @Inject(method = "alignWidgets", at = @At("TAIL"))
    private void quest_enhance$align_component_button(CallbackInfo callback_info) {
        if (this.quest_enhance$component_button != null) {
            this.quest_enhance$component_button.setPosAndSize(213, 1, 16, 16);
        }
    }

    // 为新增组件按钮向右移动原生撤销按钮
    @ModifyConstant(method = "alignWidgets", constant = @Constant(intValue = 223))
    private int quest_enhance$move_undo_button(int original_x) {
        return original_x + 16;
    }
}
