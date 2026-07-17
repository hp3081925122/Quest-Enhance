package com.quest_enhance.client;

import com.quest_enhance.QuestEnhance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.watermedia.api.player.PlayerAPI;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class VideoPlayerScreen extends Screen {
    private static final float[] SPEEDS = {0.5F, 1.0F, 1.5F, 2.0F};

    private final Screen previous_screen;
    private final String video_path;
    private final Path resolved_path;
    private final Path playback_path;
    private final Component error_message;
    private VideoPlayer player;
    private Button play_pause_button;
    private Button speed_button;
    private Button mute_button;
    private VideoProgressSlider progress_slider;
    private int speed_index = 1;
    private int volume = 100;
    private boolean muted;
    private boolean started;
    private boolean playback_error_logged;
    private long last_duration;

    private VideoPlayerScreen(
            Screen previous_screen,
            String video_path,
            Path resolved_path,
            Path playback_path,
            Component error_message
    ) {
        super(Component.translatable("quest_enhance.video.player"));
        this.previous_screen = previous_screen;
        this.video_path = video_path;
        this.resolved_path = resolved_path;
        this.playback_path = playback_path;
        this.error_message = error_message;
    }

    // 校验本地视频文件和解码后端后打开独立播放器界面
    public static void open(String video_path) {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<Path> resolved_path = QuestVideoData.resolve(video_path);
        Path playback_path = null;
        Component error_message = null;
        if (resolved_path.isEmpty()) {
            error_message = Component.translatable("quest_enhance.video.error.invalid_path", video_path);
        } else if (!Files.isRegularFile(resolved_path.get())) {
            error_message = Component.translatable("quest_enhance.video.error.missing", video_path);
        } else if (!PlayerAPI.isReady()) {
            error_message = Component.translatable("quest_enhance.video.error.backend");
        } else {
            try {
                playback_path = QuestVideoData.prepareForPlayback(resolved_path.get());
            } catch (IOException exception) {
                QuestEnhance.LOGGER.error(
                        "Failed to prepare an ASCII video path for VLC: source={}",
                        resolved_path.get(),
                        exception
                );
                error_message = Component.translatable("quest_enhance.video.error.cache");
            }
        }

        minecraft.setScreen(new VideoPlayerScreen(
                minecraft.screen,
                video_path,
                resolved_path.orElse(null),
                playback_path,
                error_message
        ));
    }

    @Override
    protected void init() {
        // 顶部保留关闭和窗口全屏按钮，不遮挡底部播放控制栏
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                button -> this.onClose()
        ).bounds(8, 8, 60, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("quest_enhance.video.fullscreen"),
                button -> this.toggleFullscreen()
        ).bounds(Math.max(72, this.width - 88), 8, 80, 20).build());

        // 底部提供进度、播放、倍速、音量和静音控制
        this.progress_slider = this.addRenderableWidget(new VideoProgressSlider(
                8,
                Math.max(30, this.height - 47),
                Math.max(80, this.width - 16),
                20
        ));
        int controls_y = Math.max(52, this.height - 24);
        this.play_pause_button = this.addRenderableWidget(Button.builder(
                Component.translatable("quest_enhance.video.pause"),
                button -> {
                    if (this.player != null) {
                        if (this.player.isEnded() || this.player.isStopped() || this.player.isBroken()) {
                            this.restartPlayer();
                        } else {
                            this.player.togglePlayback();
                        }
                    }
                }
        ).bounds(8, controls_y, 60, 20).build());
        this.speed_button = this.addRenderableWidget(Button.builder(
                this.speedText(),
                button -> {
                    if (this.player != null) {
                        this.speed_index = (this.speed_index + 1) % SPEEDS.length;
                        this.player.setSpeed(SPEEDS[this.speed_index]);
                        this.speed_button.setMessage(this.speedText());
                    }
                }
        ).bounds(72, controls_y, 64, 20).build());
        int volume_width = Math.max(70, this.width - 212);
        this.addRenderableWidget(new VideoVolumeSlider(140, controls_y, volume_width, 20));
        this.mute_button = this.addRenderableWidget(Button.builder(
                Component.translatable("quest_enhance.video.mute"),
                button -> {
                    if (this.player != null) {
                        this.muted = !this.muted;
                        this.player.setVolume(this.muted ? 0 : this.volume);
                        this.player.setMuteMode(this.muted);
                        this.mute_button.setMessage(Component.translatable(
                                this.muted ? "quest_enhance.video.unmute" : "quest_enhance.video.mute"
                        ));
                    }
                }
        ).bounds(this.width - 68, controls_y, 60, 20).build());

        // 首次初始化界面时创建唯一播放器实例并开始读取本地文件
        if (!this.started && this.error_message == null) {
            this.restartPlayer();
            this.started = true;
        }
    }

    @Override
    public void tick() {
        // 用播放器实时状态刷新按钮和进度显示
        if (this.player != null) {
            long duration = Math.max(this.player.getDuration(), this.player.getMediaInfoDuration());
            if (duration > 0L) {
                this.last_duration = duration;
            }
            this.play_pause_button.setMessage(Component.translatable(
                    this.player.isBroken()
                            ? "quest_enhance.video.retry"
                            : this.player.isEnded()
                            ? "quest_enhance.video.replay"
                            : this.player.isPaused()
                            ? "quest_enhance.video.play"
                            : "quest_enhance.video.pause"
            ));
            this.mute_button.setMessage(Component.translatable(
                    this.muted ? "quest_enhance.video.unmute" : "quest_enhance.video.mute"
            ));
            this.progress_slider.syncFromPlayer();

            // 播放器首次进入错误状态时记录现场，方便继续定位解码问题
            if (this.player.isBroken() && !this.playback_error_logged) {
                this.playback_error_logged = true;
                QuestEnhance.LOGGER.error(
                        "Video playback failed: source={}, playback={}, state={}, valid={}, size={}x{}, time={}, duration={}",
                        this.resolved_path,
                        this.playback_path,
                        this.player.getStateName(),
                        this.player.isValid(),
                        this.player.width(),
                        this.player.height(),
                        this.player.getTime(),
                        duration
                );
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouse_x, int mouse_y, float partial_tick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // 按原始宽高比将视频完整放入控制栏上方区域
        if (this.player != null && this.player.width() > 1 && this.player.height() > 1 && !this.player.isBroken()) {
            int available_height = Math.max(1, this.height - 54);
            double scale = Math.min(
                    (double) this.width / this.player.width(),
                    (double) available_height / this.player.height()
            );
            int video_width = Math.max(1, (int) Math.round(this.player.width() * scale));
            int video_height = Math.max(1, (int) Math.round(this.player.height() * scale));
            int video_x = (this.width - video_width) / 2;
            int video_y = (available_height - video_height) / 2;
            VideoRenderHelper.draw(
                    graphics,
                    this.player.texture(),
                    video_x,
                    video_y,
                    video_x + video_width,
                    video_y + video_height
            );
        } else {
            Component status = this.error_message != null
                    ? this.error_message
                    : this.player != null && this.player.isEnded()
                    ? Component.translatable("quest_enhance.video.ended")
                    : this.player != null && this.player.isBroken()
                    ? Component.translatable("quest_enhance.video.error.playback")
                    : Component.translatable("quest_enhance.video.loading");
            graphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 - 5, 0xFFFFFFFF);
        }

        // 在视频上方绘制半透明控制区和原生控件
        graphics.fill(0, Math.max(0, this.height - 52), this.width, this.height, 0xB0000000);
        graphics.drawCenteredString(this.font, this.video_path, this.width / 2, 12, 0xFFD8D8D8);
        super.render(graphics, mouse_x, mouse_y, partial_tick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouse_x, int mouse_y, float partial_tick) {
    }

    @Override
    public boolean keyPressed(int key_code, int scan_code, int modifiers) {
        // 提供播放器常用快捷操作并保留原生 Esc 返回行为
        if (this.player != null) {
            if (key_code == GLFW.GLFW_KEY_SPACE) {
                if (this.player.isEnded() || this.player.isStopped() || this.player.isBroken()) {
                    this.restartPlayer();
                } else {
                    this.player.togglePlayback();
                }
                return true;
            }
            if (key_code == GLFW.GLFW_KEY_LEFT) {
                this.player.rewind();
                return true;
            }
            if (key_code == GLFW.GLFW_KEY_RIGHT) {
                this.player.foward();
                return true;
            }
        }
        if (key_code == GLFW.GLFW_KEY_F) {
            this.toggleFullscreen();
            return true;
        }
        return super.keyPressed(key_code, scan_code, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previous_screen);
    }

    @Override
    public void removed() {
        // 离开播放器时停止解码并释放原生播放器与 OpenGL 纹理
        this.releasePlayer();
        super.removed();
    }

    // 重播时释放已结束的原生实例并创建全新的播放器
    private void restartPlayer() {
        if (this.player != null) {
            QuestEnhance.LOGGER.debug(
                    "Recreating video player: source={}, playback={}, previousState={}, muted={}, configuredVolume={}",
                    this.resolved_path,
                    this.playback_path,
                    this.player.getStateName(),
                    this.muted,
                    this.volume
            );
        }
        this.releasePlayer();
        this.last_duration = 0L;
        this.playback_error_logged = false;
        this.player = new VideoPlayer(this.minecraft);
        this.player.setVolume(this.muted ? 0 : this.volume);
        this.player.setMuteMode(this.muted);
        this.player.setSpeed(SPEEDS[this.speed_index]);
        this.player.start(this.playback_path.toUri());
    }

    // 停止解码并释放原生播放器和视频纹理
    private void releasePlayer() {
        if (this.player != null) {
            this.player.stop();
            this.player.release();
            this.player = null;
        }
    }

    // 优先读取播放状态时长，并用媒体信息和最近有效值兜底
    private long playerDuration() {
        if (this.player == null) {
            return 0L;
        }
        long duration = Math.max(this.player.getDuration(), this.player.getMediaInfoDuration());
        return duration > 0L ? duration : this.last_duration;
    }

    // 切换 Minecraft 窗口全屏状态并同步原生选项值
    private void toggleFullscreen() {
        this.minecraft.getWindow().toggleFullScreen();
        this.minecraft.options.fullscreen().set(this.minecraft.getWindow().isFullscreen());
    }

    // 根据当前倍速生成本地化按钮文本
    private Component speedText() {
        return Component.translatable(
                "quest_enhance.video.speed",
                String.format(Locale.ROOT, "%.1fx", SPEEDS[this.speed_index])
        );
    }

    // 将毫秒时间转换为播放器进度文字
    private static String formatTime(long time) {
        long total_seconds = Math.max(0L, time) / 1000L;
        long hours = total_seconds / 3600L;
        long minutes = total_seconds % 3600L / 60L;
        long seconds = total_seconds % 60L;
        return hours > 0L
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private final class VideoProgressSlider extends AbstractSliderButton {
        private VideoProgressSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.updateMessage();
        }

        // 从播放器读取进度，但实际拖动仍由原生滑块负责
        private void syncFromPlayer() {
            long duration = VideoPlayerScreen.this.playerDuration();
            long time = VideoPlayerScreen.this.player.getTime();
            if (VideoPlayerScreen.this.player.isEnded()) {
                time = duration;
            }
            this.value = duration > 0L ? Math.clamp((double) time / duration, 0.0D, 1.0D) : 0.0D;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            long duration = VideoPlayerScreen.this.playerDuration();
            long time = VideoPlayerScreen.this.player == null ? 0L : VideoPlayerScreen.this.player.getTime();
            if (VideoPlayerScreen.this.player != null && VideoPlayerScreen.this.player.isEnded()) {
                time = duration;
            }
            this.setMessage(Component.literal(formatTime(time) + " / " + formatTime(duration)));
        }

        @Override
        protected void applyValue() {
            if (VideoPlayerScreen.this.player != null) {
                long duration = VideoPlayerScreen.this.playerDuration();
                if (duration > 0L) {
                    VideoPlayerScreen.this.player.seekTo((long) (duration * this.value));
                }
            }
        }
    }

    private final class VideoVolumeSlider extends AbstractSliderButton {
        private VideoVolumeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), VideoPlayerScreen.this.volume / 100.0D);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable(
                    "quest_enhance.video.volume",
                    Math.round(this.value * 100.0D)
            ));
        }

        @Override
        protected void applyValue() {
            VideoPlayerScreen.this.volume = (int) Math.round(this.value * 100.0D);
            if (VideoPlayerScreen.this.player != null) {
                VideoPlayerScreen.this.player.setVolume(VideoPlayerScreen.this.volume);
                VideoPlayerScreen.this.muted = VideoPlayerScreen.this.volume == 0;
                VideoPlayerScreen.this.mute_button.setMessage(Component.translatable(
                        VideoPlayerScreen.this.muted
                                ? "quest_enhance.video.unmute"
                                : "quest_enhance.video.mute"
                ));
            }
        }
    }
}
