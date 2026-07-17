package com.quest_enhance.mixin;

import com.quest_enhance.client.DescriptionVideoConfigScreen;
import com.quest_enhance.client.MultilineTextEditorAccess;
import com.quest_enhance.client.VideoSelectionScreen;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftbquests.client.gui.MultilineTextEditorScreen$ToolbarPanel", remap = false)
public abstract class MultilineTextEditorToolbarMixin {
    @Shadow
    @Final
    private MultilineTextEditorScreen this$0;

    @Unique
    private SimpleTextButton quest_enhance$video_button;

    // 在描述编辑器工具栏中加入视频选择按钮
    @Inject(method = "addWidgets", at = @At("TAIL"))
    private void quest_enhance$add_video_button(CallbackInfo callback_info) {
        Panel panel = (Panel) (Object) this;
        this.quest_enhance$video_button = SimpleTextButton.create(
                panel,
                Component.translatable("quest_enhance.video.description.add"),
                Icons.CAMERA,
                mouse_button -> VideoSelectionScreen.open(
                        panel,
                        "",
                        false,
                        video_path -> {
                            if (!video_path.isBlank()) {
                                DescriptionVideoConfigScreen.open(
                                        panel,
                                        (MultilineTextEditorAccess) this.this$0,
                                        video_path
                                );
                            }
                        }
                ),
                Component.translatable("quest_enhance.video.description.add")
        );
        panel.add(this.quest_enhance$video_button);
    }

    // 将视频按钮放在原生图片和格式转换按钮之后
    @Inject(method = "alignWidgets", at = @At("TAIL"))
    private void quest_enhance$align_video_button(CallbackInfo callback_info) {
        if (this.quest_enhance$video_button != null) {
            this.quest_enhance$video_button.setPosAndSize(197, 1, 16, 16);
        }
    }

    // 为新增视频按钮向右移动原生撤销按钮
    @ModifyConstant(method = "alignWidgets", constant = @Constant(intValue = 207))
    private int quest_enhance$move_undo_button(int original_x) {
        return original_x + 16;
    }
}
