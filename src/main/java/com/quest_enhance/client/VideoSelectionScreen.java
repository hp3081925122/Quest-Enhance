package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class VideoSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final String current_video;
    private final Consumer<String> selection_callback;
    private final List<String> videos;

    private VideoSelectionScreen(
            Panel parent_panel,
            String current_video,
            boolean allow_none,
            Consumer<String> selection_callback
    ) {
        this.parent_panel = parent_panel;
        this.current_video = current_video == null ? "" : current_video;
        this.selection_callback = selection_callback;
        this.videos = new ArrayList<>();
        if (allow_none) {
            this.videos.add("");
        }
        this.videos.addAll(QuestVideoData.listAvailableVideos());
        this.setTitle(Component.translatable("quest_enhance.video.select"));
        this.setHasSearchBox(true);
    }

    // 使用 FTB 原生列表屏幕展示可搜索、可滚动的全部视频文件
    public static void open(
            Panel parent_panel,
            String current_video,
            boolean allow_none,
            Consumer<String> selection_callback
    ) {
        new VideoSelectionScreen(
                parent_panel,
                current_video,
                allow_none,
                selection_callback
        ).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        // 目录为空时显示不可产生配置的说明项
        if (this.videos.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.video.empty"),
                    Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        // 每个条目显示相对路径，并标记当前已经选择的视频
        for (String video : this.videos) {
            Component name = video.isBlank()
                    ? Component.translatable("quest_enhance.video.none")
                    : Component.literal(video);
            Component label = video.equals(this.current_video)
                    ? Component.translatable("quest_enhance.video.selected", name)
                    : name;
            panel.add(SimpleTextButton.create(
                    panel,
                    label,
                    video.isBlank() ? Icons.REMOVE : Icons.CAMERA,
                    button -> {
                        this.parent_panel.run();
                        this.selection_callback.accept(video);
                    }
            ));
        }
    }

    @Override
    public boolean onInit() {
        // 根据条目数量限制列表窗口尺寸，超出高度后交给 FTB 原生滚动条
        int target_width = Mth.clamp(420, 176, this.getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.videos.size() * 20 + 50,
                166,
                this.getWindow().getGuiScaledHeight() * 4 / 5
        );
        this.setSize(target_width, target_height);
        return super.onInit();
    }

    @Override
    public void onBack() {
        this.parent_panel.run();
    }

    @Override
    protected void doCancel() {
        this.parent_panel.run();
    }

    @Override
    protected void doAccept() {
        this.parent_panel.run();
    }
}
