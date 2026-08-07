package com.quest_enhance.mixin;

import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.client.canvas.ChapterCanvasGif;
import com.quest_enhance.client.canvas.ChapterCanvasText;
import com.quest_enhance.client.canvas.ChapterCanvasVideo;
import com.quest_enhance.client.canvas.DecorativeLineMenus;
import com.quest_enhance.client.integration.KubeJSClickEventBridge;
import com.quest_enhance.client.media.VideoSupport;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterImageButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.ImageClickAction;
import dev.ftb.mods.ftbquests.quest.QuestShape;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(value = ChapterImageButton.class, remap = false)
public abstract class ChapterImageButtonMixin {
    @Shadow
    @Final
    private QuestScreen questScreen;

    @Shadow
    @Final
    private ChapterImage chapterImage;

    // 判断图片是否由任务书增强作为可交互画布元素管理
    @Unique
    private boolean quest_enhance$is_special_canvas_image() {
        return DecorativeAnchor.isAnchor(this.chapterImage)
                || ChapterCanvasText.getTextData(this.chapterImage).isPresent()
                || ChapterCanvasVideo.getVideoData(this.chapterImage).isPresent()
                || ChapterCanvasGif.getGifData(this.chapterImage).isPresent();
    }

    // 仅为本模组特殊画布元素保留原生鼠标命中和点击入口
    @Redirect(
            method = {"checkMouseOver", "mousePressed"},
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/quest/ImageClickAction;isNone()Z"
            )
    )
    private boolean quest_enhance$allow_special_canvas_image_click(ImageClickAction click_action) {
        return click_action.isNone() && !this.quest_enhance$is_special_canvas_image();
    }

    // 拦截新版图片编辑操作，为特殊画布元素打开对应属性页
    @Redirect(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/quest/ChapterImage;onEditButtonClicked(Ljava/lang/Runnable;Lnet/minecraft/network/chat/Component;)V"
            )
    )
    private void quest_enhance$open_special_edit_screen(
            ChapterImage image,
            Runnable callback,
            Component title
    ) {
        Optional<ChapterCanvasText.TextData> text_data = ChapterCanvasText.getTextData(this.chapterImage);
        Optional<ChapterCanvasVideo.VideoData> video_data = ChapterCanvasVideo.getVideoData(this.chapterImage);
        Optional<ChapterCanvasGif.GifData> gif_data = ChapterCanvasGif.getGifData(this.chapterImage);
        boolean decorative_anchor = DecorativeAnchor.isAnchor(this.chapterImage);
        if (text_data.isEmpty() && video_data.isEmpty() && gif_data.isEmpty() && !decorative_anchor) {
            image.onEditButtonClicked(callback, title);
            return;
        }

        Component object_title = text_data.isPresent()
                ? text_data.get().component()
                : video_data.<Component>map(data -> Component.literal(data.path()))
                .or(() -> gif_data.map(data -> Component.literal(data.resource_location().toString())))
                .orElseGet(() -> Component.translatable("quest_enhance.decorative_anchor"));
        String type_key = text_data.isPresent()
                ? "quest_enhance.chapter_text"
                : video_data.isPresent()
                ? "quest_enhance.chapter_video"
                : gif_data.isPresent()
                ? "quest_enhance.chapter_gif"
                : "quest_enhance.decorative_anchor";

        // 保存时继续发送 FTB 原生章节编辑消息并刷新任务书
        ConfigGroup group = new ConfigGroup("ftbquests", accepted -> {
            if (accepted) {
                EditObjectMessage.sendToServer(this.chapterImage);
            }
            callback.run();
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
    }

    // 在已选辅助点的右键菜单中加入与任务相同的装饰线操作
    @ModifyArg(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/BaseScreen;openContextMenu(Ljava/util/List;)Ldev/ftb/mods/ftblibrary/ui/ContextMenu;"
            ),
            index = 0
    )
    private List<ContextMenuItem> quest_enhance$add_decorative_line_menu(
            List<ContextMenuItem> context_menu
    ) {
        return DecorativeAnchor.isAnchor(this.chapterImage)
                ? DecorativeLineMenus.append(context_menu, this.questScreen, this.chapterImage)
                : context_menu;
    }

    // 普通左键点击文字时投递可选 KubeJS 事件，点击视频背景时打开播放器
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_chapter_video(MouseButton button, CallbackInfo callback_info) {
        if (!button.isLeft() || Screen.hasControlDown() || Screen.hasAltDown()) {
            return;
        }

        if (((QuestScreenAccessor) (Object) this.questScreen).quest_enhance$get_file().canEdit()) {
            return;
        }

        Optional<ChapterCanvasText.TextData> textData = ChapterCanvasText.getTextData(this.chapterImage);
        if (textData.isPresent()) {
            ChapterCanvasText.TextData data = textData.get();
            KubeJSClickEventBridge.dispatch(
                    data.text(),
                    Long.toUnsignedString(this.chapterImage.getChapter().getId()),
                    this.chapterImage.getX(),
                    this.chapterImage.getY(),
                    this.chapterImage.getWidth(),
                    this.chapterImage.getHeight()
            );
            callback_info.cancel();
            return;
        }

        ChapterCanvasVideo.getVideoData(this.chapterImage).ifPresent(data -> {
            VideoSupport.open(data.path());
            callback_info.cancel();
        });
    }

    // 用辅助点、文字或视频预览替换特殊画布元素的原生图片绘制
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
        Optional<ChapterCanvasGif.GifData> gif_data = ChapterCanvasGif.getGifData(this.chapterImage);
        boolean decorative_anchor = DecorativeAnchor.isAnchor(this.chapterImage);
        if (text_data.isEmpty() && video_data.isEmpty() && gif_data.isEmpty() && !decorative_anchor) {
            return;
        }

        // 辅助点始终显示，并使用无任务图标的原生圆形外观
        if (decorative_anchor) {
            QuestScreenAccessor screen = (QuestScreenAccessor) (Object) this.questScreen;
            QuestShape circle = QuestShape.get("circle");
            boolean selected = screen.quest_enhance$get_file().canEdit()
                    && screen.quest_enhance$get_selected_objects().contains(this.chapterImage);
            circle.getShape().withColor(Color4I.DARK_GRAY).draw(graphics, x, y, width, height);
            circle.getBackground().withColor(Color4I.WHITE.withAlpha(150)).draw(graphics, x, y, width, height);
            circle.getOutline().withColor(Color4I.rgb(0x808080)).draw(graphics, x, y, width, height);
            if (selected) {
                int selection_alpha = (int) (190.0 + Math.sin(System.currentTimeMillis() * 0.003) * 50.0);
                circle.getOutline().withColor(Color4I.WHITE.withAlpha(selection_alpha)).draw(graphics, x, y, width, height);
                circle.getBackground().withColor(Color4I.WHITE.withAlpha(selection_alpha)).draw(graphics, x, y, width, height);
            }
            callback_info.cancel();
            return;
        }

        // GIF 背景逐帧更新动态纹理，并沿用原生章节图片的变换和选中效果
        if (gif_data.isPresent()) {
            QuestScreenAccessor screen = (QuestScreenAccessor) (Object) this.questScreen;
            boolean transparent = !this.chapterImage.shouldShowImage(screen.quest_enhance$get_file().selfTeamData);
            int alpha = transparent ? 100 : this.chapterImage.getAlpha();
            Icon frame = ChapterCanvasGif.getCurrentFrame(gif_data.get().resource_location()).orElse(Color4I.DARK_GRAY);
            Icon tinted_frame = frame.withTint(this.chapterImage.getColor().withAlpha(alpha));
            PoseStack pose_stack = graphics.pose();
            pose_stack.pushPose();
            if (this.chapterImage.isAlignToCorner()) {
                pose_stack.translate(x, y, 0.0F);
                pose_stack.mulPose(Axis.ZP.rotationDegrees((float) this.chapterImage.getRotation()));
                tinted_frame.draw(graphics, 0, 0, width, height);
            } else {
                pose_stack.translate(x + width / 2.0F, y + height / 2.0F, 0.0F);
                pose_stack.mulPose(Axis.ZP.rotationDegrees((float) this.chapterImage.getRotation()));
                tinted_frame.draw(graphics, -width / 2, -height / 2, width, height);
            }
            pose_stack.popPose();
            if (screen.quest_enhance$get_selected_objects().contains(this.chapterImage)) {
                int selection_alpha = (int) (45.0 + Math.sin(System.currentTimeMillis() * 0.003) * 20.0);
                Color4I.WHITE.withAlpha(selection_alpha).draw(graphics, x, y, width, height);
            }
            callback_info.cancel();
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
        // 先按画布框适配文字，再叠加文字自身的缩放倍率。
        float scale = Math.max(0.001F, Math.min((float) width / text_width, (float) height / text_height))
                * (float) Math.max(0.05D, Math.min(10.0D, data.scale()));
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
