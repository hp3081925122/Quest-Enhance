package com.quest_enhance.mixin;

import com.quest_enhance.client.ChapterCanvasText;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.util.ConfigQuestObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Mixin(value = ChapterImage.class, remap = false)
public abstract class ChapterImageMixin {
    @Shadow
    private Chapter chapter;

    @Shadow
    private double x;

    @Shadow
    private double y;

    @Shadow
    private double width;

    @Shadow
    private double height;

    @Shadow
    private double rotation;

    @Shadow
    private Color4I color;

    @Shadow
    private int alpha;

    @Shadow
    @Final
    private List<String> hover;

    @Shadow
    private boolean editorsOnly;

    @Shadow
    private boolean alignToCorner;

    @Shadow
    private Quest dependency;

    @Shadow
    private int order;

    // 为画布文字提供专用属性页，隐藏无意义的图片和点击字段
    @Inject(method = "fillConfigGroup", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$fill_text_config(ConfigGroup config, CallbackInfo callback_info) {
        ChapterImage image = (ChapterImage) (Object) this;
        Optional<ChapterCanvasText.TextData> text_data = ChapterCanvasText.getTextData(image);
        if (text_data.isEmpty()) {
            return;
        }
        ChapterCanvasText.TextData data = text_data.get();

        // 添加文字内容和原生章节图片共有的布局属性
        config.addString(
                "text",
                data.text(),
                value -> ChapterCanvasText.setText(image, value),
                "",
                Pattern.compile(".+")
        ).setNameKey("quest_enhance.chapter_text.text");

        // 构建当前全部已加载字体组成的原生枚举选择器
        List<ResourceLocation> font_ids = ChapterCanvasText.getAvailableFonts();
        if (!font_ids.contains(data.font())) {
            font_ids.add(data.font());
        }
        NameMap<ResourceLocation> fonts = NameMap.of(ChapterCanvasText.DEFAULT_FONT, font_ids)
                .name(font -> Component.literal(font.toString()))
                .create();
        config.addEnum(
                "font",
                data.font(),
                value -> ChapterCanvasText.setFont(image, value),
                fonts,
                ChapterCanvasText.DEFAULT_FONT
        ).setNameKey("quest_enhance.chapter_text.font");
        config.addDouble("x", this.x, value -> this.x = value, 0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        config.addDouble("y", this.y, value -> this.y = value, 0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        config.addDouble("width", this.width, value -> this.width = value, 1.0, 0.0, Double.POSITIVE_INFINITY);
        config.addDouble("height", this.height, value -> this.height = value, 1.0, 0.0, Double.POSITIVE_INFINITY);
        config.addDouble("rotation", this.rotation, value -> this.rotation = value, 0.0, -180.0, 180.0);
        config.addColor("color", this.color, value -> this.color = value, Color4I.WHITE)
                .setNameKey("quest_enhance.chapter_text.color");
        config.addInt("order", this.order, value -> this.order = value, 0, Integer.MIN_VALUE, Integer.MAX_VALUE)
                .setNameKey("quest_enhance.chapter_text.order");
        config.addInt("alpha", this.alpha, value -> this.alpha = value, 255, 0, 255)
                .setNameKey("quest_enhance.chapter_text.alpha");
        config.addList("hover", this.hover, new StringConfig(), "");
        config.addBool("dev", this.editorsOnly, value -> this.editorsOnly = value, false);
        config.addBool("corner", this.alignToCorner, value -> this.alignToCorner = value, false);

        // 保留原生依赖任务控制，确保文字可按任务进度显示
        Predicate<QuestObjectBase> dependency_types = object -> object == null || object instanceof Quest;
        ((ConfigQuestObject) config.add(
                "dependency",
                new ConfigQuestObject(dependency_types),
                this.dependency,
                value -> this.dependency = value,
                null
        )).setNameKey("ftbquests.dependency");
        callback_info.cancel();
    }

    // 文字始终按字体原始比例渲染，不显示图片宽高修复按钮
    @Inject(method = "isAspectRatioOff", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$disable_text_aspect_ratio_fix(CallbackInfoReturnable<Boolean> callback_info) {
        if (ChapterCanvasText.getTextData((ChapterImage) (Object) this).isPresent()) {
            callback_info.setReturnValue(false);
        }
    }

    // 在删除确认等原生界面中使用实际文字作为对象标题
    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$get_text_title(CallbackInfoReturnable<Component> callback_info) {
        ChapterCanvasText.getTextData((ChapterImage) (Object) this)
                .ifPresent(data -> callback_info.setReturnValue(data.component()));
    }
}
