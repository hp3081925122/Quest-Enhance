package com.quest_enhance.mixin;

import com.quest_enhance.client.ChapterCanvasText;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterImageButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
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

    // 为画布文字打开标题和类型都正确的原生属性编辑页
    @Inject(method = "openEditScreen", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_text_edit_screen(CallbackInfo callback_info) {
        Optional<ChapterCanvasText.TextData> text_data = ChapterCanvasText.getTextData(this.chapterImage);
        if (text_data.isEmpty()) {
            return;
        }
        ChapterCanvasText.TextData data = text_data.get();

        // 保存时继续发送 FTB 原生章节编辑消息并刷新当前按钮
        ChapterImageButton button = (ChapterImageButton) (Object) this;
        ConfigGroup group = new ConfigGroup("ftbquests", accepted -> {
            if (accepted) {
                new EditObjectMessage(this.chapterImage.getChapter()).sendToServer();
            }
            button.run();
        }) {
            // 用实际文字和“文字”类型替换原生的颜色值与“图片”类型
            @Override
            public Component getName() {
                MutableComponent type = Component.literal(" [")
                        .append(Component.translatable("quest_enhance.chapter_text"))
                        .append("]")
                        .withStyle(ChatFormatting.AQUA);
                return Component.empty()
                        .append(data.component().copy().withStyle(ChatFormatting.UNDERLINE))
                        .append(type);
            }
        };
        this.chapterImage.fillConfigGroup(group.getOrCreateSubgroup("chapter").getOrCreateSubgroup("image"));

        // 沿用 FTB 的属性编辑屏幕显示文字专用配置项
        new EditConfigScreen(group) {
            @Override
            public Component getTitle() {
                return group.getName();
            }
        }.openGui();
        callback_info.cancel();
    }

    // 阻止原生左键把文字标记当作链接执行
    @Redirect(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/quest/ChapterImage;getClick()Ljava/lang/String;"
            )
    )
    private String quest_enhance$hide_text_click(ChapterImage image, MouseButton button) {
        return ChapterCanvasText.getTextData(image).isPresent() ? "" : image.getClick();
    }

    // 用保持原始宽高比的文字替换空图片绘制
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
        if (text_data.isEmpty()) {
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
