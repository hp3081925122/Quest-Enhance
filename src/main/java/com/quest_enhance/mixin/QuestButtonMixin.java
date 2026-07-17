package com.quest_enhance.mixin;

import com.quest_enhance.client.QuestEnhanceClientConfig;
import com.quest_enhance.client.KillTaskEntityPreview;
import com.quest_enhance.client.QuestEntityModel;
import com.quest_enhance.client.QuestVideoData;
import com.quest_enhance.client.VideoPlayerScreen;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestButton.class, remap = false)
public abstract class QuestButtonMixin {
    @Shadow
    @Final
    Quest quest;

    @Unique
    private KillTaskEntityPreview quest_enhance$entity_preview;

    // 阻止原图标绘制，为手动模型或自动击杀任务模型保留节点中央区域
    @Redirect(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/icon/Icon;isEmpty()Z",
                    ordinal = 0
            )
    )
    private boolean quest_enhance$hide_replaced_icon(Icon icon) {
        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) this.quest).quest_enhance$get_raw_icon();
        return this.quest_enhance$get_entity_model() != null
                || QuestVideoData.isPlaceholder(raw_icon)
                || icon.isEmpty();
    }

    // 在配置了视频的任务节点右下角绘制独立播放区域
    @Inject(method = "draw", at = @At("TAIL"))
    private void quest_enhance$draw_video_button(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) this.quest).quest_enhance$get_raw_icon();
        if (QuestVideoData.getVideo(raw_icon).isEmpty()) {
            return;
        }

        int button_size = Math.max(8, Math.min(14, Math.min(width, height) / 2));
        int button_x = x + width - button_size;
        int button_y = y + height - button_size;
        graphics.fill(button_x, button_y, button_x + button_size, button_y + button_size, 0xD0000000);
        graphics.drawCenteredString(
                Minecraft.getInstance().font,
                Component.literal("▶"),
                button_x + button_size / 2,
                button_y + (button_size - 8) / 2,
                0xFFFFFFFF
        );
    }

    // 只拦截播放区域的普通左键，节点其余区域仍按 FTB 原逻辑打开任务
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_quest_video(MouseButton mouse_button, CallbackInfo callback_info) {
        if (!mouse_button.isLeft() || Screen.hasControlDown() || Screen.hasAltDown()) {
            return;
        }

        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) this.quest).quest_enhance$get_raw_icon();
        String video_path = QuestVideoData.getVideo(raw_icon).orElse(null);
        if (video_path == null) {
            return;
        }

        QuestButton button = (QuestButton) (Object) this;
        int button_size = Math.max(8, Math.min(14, Math.min(button.getWidth(), button.getHeight()) / 2));
        int mouse_x = button.getMouseX();
        int mouse_y = button.getMouseY();
        if (mouse_x >= button.getX() + button.getWidth() - button_size
                && mouse_y >= button.getY() + button.getHeight() - button_size) {
            VideoPlayerScreen.open(video_path);
            callback_info.cancel();
        }
    }

    // 在节点背景之后、状态覆盖图标之前绘制实体模型
    @Inject(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/GuiHelper;setupDrawing()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void quest_enhance$draw_quest_entity_model(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        ResourceLocation entity_id = this.quest_enhance$get_entity_model();
        if (entity_id == null) {
            return;
        }

        // 按 FTB 原节点图标缩放规则计算模型占用区域
        if (this.quest_enhance$entity_preview == null) {
            this.quest_enhance$entity_preview = new KillTaskEntityPreview();
        }
        int model_size = Math.max(1, Math.min(
                Math.min(width, height),
                Math.round(width * 0.6666667F * (float) this.quest.getIconScale())
        ));
        int model_x = x + (width - model_size) / 2;
        int model_y = y + (height - model_size) / 2;
        this.quest_enhance$entity_preview.render(
                entity_id,
                graphics,
                model_x,
                model_y,
                model_size,
                model_size
        );
    }

    // 手动模型优先，普通自定义图标次之，最后使用单个击杀任务的目标实体
    @Unique
    private ResourceLocation quest_enhance$get_entity_model() {
        if (!QuestEnhanceClientConfig.RENDER_KILL_TASK_ENTITY_MODELS.get()) {
            return null;
        }

        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) this.quest).quest_enhance$get_raw_icon();
        ResourceLocation explicit_model = QuestEntityModel.getEntityModel(raw_icon).orElse(null);
        if (explicit_model != null) {
            return explicit_model;
        }
        if ((!raw_icon.isEmpty() && !QuestVideoData.isPlaceholder(raw_icon))
                || this.quest.getTasks().size() != 1) {
            return null;
        }

        return this.quest.getTasks().iterator().next() instanceof KillTask kill_task
                ? ((KillTaskAccessor) kill_task).quest_enhance$get_entity()
                : null;
    }
}
