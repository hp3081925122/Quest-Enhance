package com.quest_enhance.mixin;

import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.ChapterCanvasText;
import com.quest_enhance.client.ChapterCanvasVideo;
import com.quest_enhance.client.DecorativeLineMenus;
import com.quest_enhance.client.VideoSupport;
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
    @Unique
    private boolean quest_enhance$anchor_selected;

    @Unique
    private boolean quest_enhance$anchor_selection_state_known;

    @Shadow
    @Final
    private QuestScreen questScreen;

    @Shadow
    @Final
    private ChapterImage chapterImage;

    // 为画布文字、视频或辅助点打开标题和类型都正确的原生属性编辑页
    @Inject(method = "openEditScreen", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_special_edit_screen(CallbackInfo callback_info) {
        Optional<ChapterCanvasText.TextData> text_data = ChapterCanvasText.getTextData(this.chapterImage);
        Optional<ChapterCanvasVideo.VideoData> video_data = ChapterCanvasVideo.getVideoData(this.chapterImage);
        boolean decorative_anchor = DecorativeAnchor.isAnchor(this.chapterImage);
        if (text_data.isEmpty() && video_data.isEmpty() && !decorative_anchor) {
            return;
        }

        Component object_title = text_data.isPresent()
                ? text_data.get().component()
                : video_data.<Component>map(data -> Component.literal(data.path()))
                .orElseGet(() -> Component.translatable("quest_enhance.decorative_anchor"));
        String type_key = text_data.isPresent()
                ? "quest_enhance.chapter_text"
                : video_data.isPresent()
                ? "quest_enhance.chapter_video"
                : "quest_enhance.decorative_anchor";

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

    // 阻止原生左键把特殊画布标记当作普通链接执行
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
                || DecorativeAnchor.isAnchor(image)
                ? ""
                : image.getClick();
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
        boolean decorative_anchor = DecorativeAnchor.isAnchor(this.chapterImage);
        if (text_data.isEmpty() && video_data.isEmpty() && !decorative_anchor) {
            return;
        }

        // 辅助点始终显示，并使用无任务图标的原生圆形外观
        if (decorative_anchor) {
            QuestScreenAccessor screen = (QuestScreenAccessor) (Object) this.questScreen;
            QuestShape circle = QuestShape.get("circle");
            boolean selected = screen.quest_enhance$get_file().canEdit()
                    && screen.quest_enhance$get_selected_objects().contains(this.chapterImage);
            if (!this.quest_enhance$anchor_selection_state_known || selected != this.quest_enhance$anchor_selected) {
                QuestEnhance.LOGGER.debug(
                        "Decorative anchor selection changed: node={}, selected={}",
                        DecorativeAnchor.nodeKey(this.chapterImage).orElse("unknown"),
                        selected
                );
                this.quest_enhance$anchor_selected = selected;
                this.quest_enhance$anchor_selection_state_known = true;
            }
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
