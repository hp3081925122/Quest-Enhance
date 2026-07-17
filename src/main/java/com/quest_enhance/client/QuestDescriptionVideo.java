package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public final class QuestDescriptionVideo {
    public static final String CLICK_PREFIX = "quest_enhance_video/";
    private static final String PROPERTY = "quest_enhance_video";
    private static final String LABEL_PROPERTY = "quest_enhance_video_label";
    private static final String LEGACY_TEXT_PROPERTY = "quest_enhance_video_text";

    private QuestDescriptionVideo() {
    }

    // 在 FTB Library 解析任务描述时识别本模组的视频标记
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ClientTextComponentUtils.addCustomParser(QuestDescriptionVideo::parse));
    }

    // 将视频路径编码保存，并把显示文字以可直接编辑的明文写入描述标记
    public static String createMarkup(String video_path, String display_text) {
        String encoded_path = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(video_path.getBytes(StandardCharsets.UTF_8));
        String escaped_text = display_text
                .replace("%", "%25")
                .replace("{", "%7B")
                .replace("}", "%7D")
                .replace(" ", "%20");
        return "{" + PROPERTY + ":" + encoded_path + " " + LABEL_PROPERTY + ":" + escaped_text + "}";
    }

    // 从描述属性或点击事件中还原并校验可分发的视频相对路径
    public static Optional<String> decodePath(String encoded_path) {
        try {
            String video_path = new String(
                    Base64.getUrlDecoder().decode(encoded_path),
                    StandardCharsets.UTF_8
            );
            return QuestVideoData.normalize(video_path);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    // 把视频标记渲染为可点击的播放入口
    private static Component parse(String raw_text, Map<String, String> properties) {
        String encoded_path = properties.get(PROPERTY);
        if (encoded_path == null) {
            return null;
        }

        Optional<String> video_path = decodePath(encoded_path);
        if (video_path.isEmpty()) {
            return Component.translatable("quest_enhance.video.description.invalid")
                    .withStyle(ChatFormatting.RED);
        }

        String file_name = Path.of(video_path.get()).getFileName().toString();
        String display_text = Component.translatable("quest_enhance.video.description.play", file_name).getString();
        String plain_text = properties.get(LABEL_PROPERTY);
        if (plain_text != null) {
            display_text = plain_text
                    .replace("%7B", "{")
                    .replace("%7D", "}")
                    .replace("%25", "%");
        } else if (properties.containsKey(LEGACY_TEXT_PROPERTY)) {
            try {
                display_text = new String(
                        Base64.getUrlDecoder().decode(properties.get(LEGACY_TEXT_PROPERTY)),
                        StandardCharsets.UTF_8
                );
            } catch (IllegalArgumentException exception) {
                return Component.translatable("quest_enhance.video.description.invalid")
                        .withStyle(ChatFormatting.RED);
            }
        }

        Style style = Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.CHANGE_PAGE,
                        CLICK_PREFIX + encoded_path
                ))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("quest_enhance.video.description.tooltip")
                ));
        return Component.literal(display_text)
                .withStyle(style);
    }
}
