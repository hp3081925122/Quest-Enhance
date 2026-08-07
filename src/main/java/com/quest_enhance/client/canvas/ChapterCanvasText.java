package com.quest_enhance.client.canvas;

import com.quest_enhance.common.canvas.ChapterCanvasData;
import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ChapterCanvasText {
    public static final ResourceLocation DEFAULT_FONT = Minecraft.DEFAULT_FONT;

    private ChapterCanvasText() {
    }

    // 判断章节图片是否是本模组保存的画布文字，并读取文字与字体
    public static Optional<TextData> getTextData(ChapterImage image) {
        return ChapterCanvasData.getText(image, DEFAULT_FONT)
                .map(data -> new TextData(data.text(), data.font(), data.scale()));
    }

    // 修改文字时保留字体，并继续使用 FTB 原生字段保存与同步
    public static void setText(ChapterImage image, String text) {
        TextData current = getTextData(image).orElse(new TextData("", DEFAULT_FONT, 1.0D));
        setTextData(image, new TextData(text, current.font(), current.scale()));
    }

    // 修改字体时保留文字内容
    public static void setFont(ChapterImage image, ResourceLocation font) {
        getTextData(image).ifPresent(data -> setTextData(image, new TextData(data.text(), font, data.scale())));
    }

    // 修改文字缩放倍率时保留文字内容和字体。
    public static void setScale(ChapterImage image, double scale) {
        getTextData(image).ifPresent(data -> setTextData(image, new TextData(data.text(), data.font(), scale)));
    }

    // 读取当前全部资源包中可供文字组件使用的字体定义
    public static List<ResourceLocation> getAvailableFonts() {
        Set<ResourceLocation> fonts = new LinkedHashSet<>();
        fonts.add(DEFAULT_FONT);
        Minecraft.getInstance().getResourceManager()
                .listResources("font", location -> location.getPath().endsWith(".json")
                        && !location.getPath().startsWith("font/include/"))
                .keySet()
                .stream()
                .map(location -> ResourceLocation.fromNamespaceAndPath(
                        location.getNamespace(),
                        location.getPath().substring("font/".length(), location.getPath().length() - ".json".length())
                ))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(fonts::add);
        return new ArrayList<>(fonts);
    }

    // 用长度前缀保存字体标识，保证文字本身可以包含任意分隔符
    private static void setTextData(ChapterImage image, TextData data) {
        ((ChapterImageAccessor) (Object) image).quest_enhance$set_click(
                ChapterCanvasData.textClick(data.text(), data.font(), data.scale())
        );
    }

    // 按当前节点尺寸创建与文字原始比例一致的画布对象
    public static ChapterImage create(
            Chapter chapter,
            String text,
            double x,
            double y,
            double quest_button_size,
            Theme theme
    ) {
        ChapterImage image = new ChapterImage(chapter).setPosition(x, y);
        ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
        double safe_button_size = Math.max(1.0, quest_button_size);
        TextData data = new TextData(text, DEFAULT_FONT, 1.0D);
        accessor.quest_enhance$set_width(Math.max(0.25, (theme.getStringWidth(data.component()) + 2.0) / safe_button_size));
        accessor.quest_enhance$set_height(Math.max(0.25, (theme.getFontHeight() + 2.0) / safe_button_size));
        setTextData(image, data);
        return image;
    }

    public record TextData(String text, ResourceLocation font, double scale) {
        // 创建携带字体样式的文字组件供测量与绘制共同使用
        public Component component() {
            return Component.literal(this.text).withStyle(style -> style.withFont(this.font));
        }
    }
}
