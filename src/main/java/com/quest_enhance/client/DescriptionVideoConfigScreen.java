package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class DescriptionVideoConfigScreen {
    private DescriptionVideoConfigScreen() {
    }

    // 选择视频后让制作者修改任务描述中显示的播放文字
    public static void open(
            Panel parent_panel,
            MultilineTextEditorAccess editor,
            String video_path
    ) {
        String file_name = Path.of(video_path).getFileName().toString();
        String[] display_text = {
                Component.translatable("quest_enhance.video.description.play", file_name).getString()
        };

        // 确认时插入带自定义文字的视频标记，取消时只返回描述编辑器
        ConfigGroup group = new ConfigGroup("quest_enhance", accepted -> {
            if (accepted) {
                editor.quest_enhance$insert_at_end_of_line(
                        "\n" + QuestDescriptionVideo.createMarkup(video_path, display_text[0])
                );
            }
            parent_panel.run();
        }) {
            @Override
            public Component getName() {
                return Component.translatable("quest_enhance.video.description.settings");
            }
        };
        group.addString(
                "text",
                display_text[0],
                value -> display_text[0] = value,
                display_text[0],
                Pattern.compile(".+")
        ).setNameKey("quest_enhance.video.description.text");

        // 使用 FTB 原生配置界面保持按钮和输入行为一致
        new EditConfigScreen(group) {
            @Override
            public Component getTitle() {
                return group.getName();
            }
        }.openGui();
    }
}
