package com.quest_enhance.mixin;

import com.quest_enhance.client.ChapterCanvasText;
import com.quest_enhance.client.ChapterCanvasVideo;
import com.quest_enhance.client.VideoSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterImageButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(value = ChapterImageButton.class, remap = false)
public abstract class ChapterImageButtonMixin {
    @Shadow
    @Final
    private QuestScreen questScreen;

    @Shadow
    @Final
    private ChapterImage chapterImage;

    // 为画布文字或视频打开标题和类型都正确的原生属性编辑页
    @Inject(method = "openEditScreen", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_special_edit_screen(CallbackInfo callback_info) {
        Optional<ChapterCanvasText.TextData> text_data = ChapterCanvasText.getTextData(this.chapterImage);
        Optional<ChapterCanvasVideo.VideoData> video_data = ChapterCanvasVideo.getVideoData(this.chapterImage);
        if (text_data.isEmpty() && video_data.isEmpty()) {
            return;
        }

        Component object_title = text_data.isPresent()
                ? text_data.get().component()
                : Component.literal(video_data.get().path());
        String type_key = text_data.isPresent()
                ? "quest_enhance.chapter_text"
                : "quest_enhance.chapter_video";

        // 保存时继续发送 FTB 原生章节编辑消息并刷新当前按钮
        ChapterImageButton button = (ChapterImageButton) (Object) this;
        ConfigGroup group = new ConfigGroup("ftbquests", accepted -> {
            if (accepted) {
                EditObjectMessage.sendToServer(this.chapterImage.getChapter());
            }
            button.run();
        }) {
            // 用实际内容和特殊元素类型替换原生的颜色值与“图片”类型
            @Override
            public Component getName() {
                MutableComponent type = Component.literal(" [")
                        .append(Component.translatable(type_key))
                        .append("]")
                        .withStyle(ChatFormatting.AQUA);
                return Component.empty()
                        .append(object_title.copy().withStyle(ChatFormatting.UNDERLINE))
                        .append(type);
            }
        };
        this.chapterImage.fillConfigGroup(group.getOrCreateSubgroup("chapter").getOrCreateSubgroup("image"));

        // 沿用 FTB 的属性编辑屏幕显示特殊元素专用配置项
        new EditConfigScreen(group) {
            @Override
            public Component getTitle() {
                return group.getName();
            }
        }.openGui();
        callback_info.cancel();
    }

    // 阻止原生左键把文字或视频标记当作普通链接执行
    @Redirect(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/quest/ChapterImage;getClick()Ljava/lang/String;"
            )
    )
    private String quest_enhance$hide_special_click(ChapterImage image, MouseButton button) {
        return ChapterCanvasText.getTextData(image).isPresent()
                || ChapterCanvasVideo.getVideoData(image).isPresent()
                ? ""
                : image.getClick();
    }

    // 普通左键点击视频背景时打开播放器，编辑器快捷键仍交给 FTB 处理
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_chapter_video(MouseButton button, CallbackInfo callback_info) {
        if (!button.isLeft() || Screen.hasControlDown() || Screen.hasAltDown()) {
            return;
        }

        ChapterCanvasVideo.getVideoData(this.chapterImage).ifPresent(data -> {
            VideoSupport.open(data.path());
            callback_info.cancel();
        });
    }

    // 用文字或视频预览替换特殊画布元素的原生图片绘制
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$draw_chapter_text(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        Optional<ChapterCanvasText.TextData> text_data = ChapterCanvasText.getTextData(this.chapterImage);
        Optional<ChapterCanvasVideo.VideoData> video_data = ChapterCanvasVideo.getVideoData(this.chapterImage);
        if (text_data.isEmpty() && video_data.isEmpty()) {
            return;
        }

        // 视频背景使用深色预览和播放图标，不在章节画布中启动解码器
        if (video_data.isPresent()) {
            QuestScreenAccessor screen = (QuestScreenAccessor) (Object) this.questScreen;
            boolean transparent = !this.chapterImage.shouldShowImage(screen.quest_enhance$get_file().selfTeamData);
            int alpha = transparent ? 100 : this.chapterImage.getAlpha();
            Icon cover = this.chapterImage.getImage().isEmpty()
                    ? Color4I.DARK_GRAY
                    : this.chapterImage.getImage();
            Icon tinted_cover = cover.withTint(this.chapterImage.getColor().withAlpha(alpha));
            Component play_icon = Component.literal("▶");
            int icon_width = theme.getStringWidth(play_icon);
            int icon_height = theme.getFontHeight();

            // 按原生章节图片的对齐和旋转方式绘制预览区域
            PoseStack pose_stack = graphics.pose();
            pose_stack.pushPose();
            if (this.chapterImage.isAlignToCorner()) {
                pose_stack.translate(x, y, 0.0F);
                pose_stack.mulPose(Axis.ZP.rotationDegrees((float) this.chapterImage.getRotation()));
                tinted_cover.draw(graphics, 0, 0, width, height);
                theme.drawString(
                        graphics,
                        play_icon,
                        (width - icon_width) / 2,
                        (height - icon_height) / 2,
                        Color4I.WHITE.withAlpha(alpha),
                        2
                );
            } else {
                pose_stack.translate(x + width / 2.0F, y + height / 2.0F, 0.0F);
                pose_stack.mulPose(Axis.ZP.rotationDegrees((float) this.chapterImage.getRotation()));
                tinted_cover.draw(graphics, -width / 2, -height / 2, width, height);
                theme.drawString(
                        graphics,
                        play_icon,
                        -icon_width / 2,
                        -icon_height / 2,
                        Color4I.WHITE.withAlpha(alpha),
                        2
                );
            }
            pose_stack.popPose();

            // 在编辑器中沿用图片对象的选中闪烁效果
            if (screen.quest_enhance$get_selected_objects().contains(this.chapterImage)) {
                int selection_alpha = (int) (45.0 + Math.sin(System.currentTimeMillis() * 0.003) * 20.0);
                Color4I.WHITE.withAlpha(selection_alpha).draw(graphics, x, y, width, height);
            }
            callback_info.cancel();
            return;
        }

        ChapterCanvasText.TextData data = text_data.get();
        Component text = data.component();

        // 计算保持字体比例且完整放入当前画布框的缩放值
        int text_width = Math.max(1, theme.getStringWidth(text));
        int text_height = Math.max(1, theme.getFontHeight());
        float scale = Math.max(0.001F, Math.min((float) width / text_width, (float) height / text_height));
        QuestScreenAccessor screen = (QuestScreenAccessor) (Object) this.questScreen;
        boolean transparent = !this.chapterImage.shouldShowImage(screen.quest_enhance$get_file().selfTeamData);
        int alpha = transparent ? 100 : this.chapterImage.getAlpha();
        Color4I color = this.chapterImage.getColor().withAlpha(alpha);

        // 按原生章节图片的中心或左上角对齐方式应用旋转和缩放
        PoseStack pose_stack = graphics.pose();
        pose_stack.pushPose();
        if (this.chapterImage.isAlignToCorner()) {
            pose_stack.translate(x, y, 0.0F);
            pose_stack.mulPose(Axis.ZP.rotationDegrees((float) this.chapterImage.getRotation()));
            pose_stack.scale(scale, scale, 1.0F);
            theme.drawString(graphics, text, 0, 0, color, 2);
        } else {
            pose_stack.translate(x + width / 2.0F, y + height / 2.0F, 0.0F);
            pose_stack.mulPose(Axis.ZP.rotationDegrees((float) this.chapterImage.getRotation()));
            pose_stack.scale(scale, scale, 1.0F);
            theme.drawString(graphics, text, -text_width / 2, -text_height / 2, color, 2);
        }
        pose_stack.popPose();

        // 在编辑器中沿用图片对象的选中闪烁效果
        if (screen.quest_enhance$get_selected_objects().contains(this.chapterImage)) {
            int selection_alpha = (int) (45.0 + Math.sin(System.currentTimeMillis() * 0.003) * 20.0);
            Color4I.WHITE.withAlpha(selection_alpha).draw(graphics, x, y, width, height);
        }
        callback_info.cancel();
    }
}
