package com.quest_enhance.client;

import com.quest_enhance.QuestEnhance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class QuestVideoData {
    private static final String VIDEO_TAG = "quest_enhance_video";
    private static final String PLACEHOLDER_TAG = "quest_enhance_video_placeholder";

    private QuestVideoData() {
    }

    // 从任务原生图标的自定义数据中读取视频相对路径
    public static Optional<String> getVideo(ItemStack icon_stack) {
        CompoundTag tag = icon_stack.getTag();
        if (tag == null || !tag.contains(VIDEO_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }

        return normalize(tag.getString(VIDEO_TAG));
    }

    // 判断当前物品是否只是为了让 FTB 保存视频数据而创建的隐藏占位图标
    public static boolean isPlaceholder(ItemStack icon_stack) {
        CompoundTag tag = icon_stack.getTag();
        return tag != null && tag.getBoolean(PLACEHOLDER_TAG);
    }

    // 在不覆盖普通图标和实体模型数据的前提下更新视频路径
    public static ItemStack withVideo(ItemStack original_stack, String video_path) {
        Optional<String> normalized_path = normalize(video_path);
        if (normalized_path.isEmpty()) {
            if (original_stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            CompoundTag current_tag = original_stack.getTag();
            boolean placeholder = current_tag != null && current_tag.getBoolean(PLACEHOLDER_TAG);
            ItemStack updated_stack = original_stack.copy();
            CompoundTag updated_tag = updated_stack.getTag();
            if (updated_tag != null) {
                updated_tag.remove(VIDEO_TAG);
                updated_tag.remove(PLACEHOLDER_TAG);
            }
            return placeholder ? ItemStack.EMPTY : updated_stack;
        }

        ItemStack updated_stack = original_stack.isEmpty() ? new ItemStack(Items.BARRIER) : original_stack.copy();
        CompoundTag updated_tag = updated_stack.getOrCreateTag();
        updated_tag.putString(VIDEO_TAG, normalized_path.get());
        if (original_stack.isEmpty()) {
            updated_tag.putBoolean(PLACEHOLDER_TAG, true);
        }
        return updated_stack;
    }

    // 将配置中的相对路径解析到整合包可分发的视频目录，并阻止路径越界
    public static Optional<Path> resolve(String video_path) {
        Optional<String> normalized_path = normalize(video_path);
        if (normalized_path.isEmpty()) {
            return Optional.empty();
        }

        Path video_root = videoRoot();
        Path resolved_path = video_root.resolve(normalized_path.get()).normalize();
        return resolved_path.startsWith(video_root) ? Optional.of(resolved_path) : Optional.empty();
    }

    // 为无法正确读取中文本地路径的 VLC 创建纯 ASCII 播放入口
    public static Path prepareForPlayback(Path source_path) throws IOException {
        Path normalized_source = source_path.toAbsolutePath().normalize();
        if (normalized_source.toString().chars().allMatch(character -> character <= 0x7F)) {
            return normalized_source;
        }

        String source_name = normalized_source.getFileName().toString();
        int extension_index = source_name.lastIndexOf('.');
        String extension = extension_index >= 0
                ? source_name.substring(extension_index).toLowerCase(Locale.ROOT)
                : "";
        String cache_name = UUID.nameUUIDFromBytes(
                normalized_source.toString().getBytes(StandardCharsets.UTF_8)
        ) + extension;

        // 同盘缓存优先使用硬链接，避免复制大型视频
        Path source_root = normalized_source.getRoot();
        if (source_root != null) {
            Path link_directory = source_root.resolve("quest_enhance_video_cache");
            Path linked_path = link_directory.resolve(cache_name);
            try {
                Files.createDirectories(link_directory);
                if (Files.isRegularFile(linked_path) && Files.isSameFile(linked_path, normalized_source)) {
                    linked_path.toFile().deleteOnExit();
                    return linked_path;
                }
                Files.deleteIfExists(linked_path);
                Files.createLink(linked_path, normalized_source);
                linked_path.toFile().deleteOnExit();
                QuestEnhance.LOGGER.debug(
                        "Created ASCII video hard link: source={}, cache={}",
                        normalized_source,
                        linked_path
                );
                return linked_path;
            } catch (IOException | UnsupportedOperationException | SecurityException exception) {
                QuestEnhance.LOGGER.debug(
                        "Failed to create ASCII video hard link, falling back to a temporary copy: source={}",
                        normalized_source,
                        exception
                );
            }
        }

        // 硬链接不可用时在系统临时目录保留一份可复用副本
        Path copy_directory = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("quest_enhance_video_cache")
                .toAbsolutePath()
                .normalize();
        if (!copy_directory.toString().chars().allMatch(character -> character <= 0x7F)) {
            throw new IOException("No ASCII cache directory is available for VLC");
        }
        Files.createDirectories(copy_directory);
        Path copied_path = copy_directory.resolve(cache_name);
        boolean current_copy = Files.isRegularFile(copied_path)
                && Files.size(copied_path) == Files.size(normalized_source)
                && Files.getLastModifiedTime(copied_path).equals(Files.getLastModifiedTime(normalized_source));
        if (!current_copy) {
            Files.copy(
                    normalized_source,
                    copied_path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
            );
            QuestEnhance.LOGGER.debug(
                    "Created ASCII video copy: source={}, cache={}",
                    normalized_source,
                    copied_path
            );
        }
        copied_path.toFile().deleteOnExit();
        return copied_path;
    }

    // 扫描视频目录并返回可直接写入任务配置的排序相对路径
    public static List<String> listAvailableVideos() {
        Path video_root = videoRoot();
        if (!Files.isDirectory(video_root)) {
            return List.of();
        }

        try (var files = Files.walk(video_root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".mp4")
                                || name.endsWith(".webm")
                                || name.endsWith(".mkv")
                                || name.endsWith(".mov")
                                || name.endsWith(".avi")
                                || name.endsWith(".m4v")
                                || name.endsWith(".ogv");
                    })
                    .map(video_root::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException exception) {
            QuestEnhance.LOGGER.error("Failed to scan configured videos", exception);
            return List.of();
        }
    }

    // 统一保存为使用正斜杠的可分发相对路径
    public static Optional<String> normalize(String video_path) {
        if (video_path == null || video_path.isBlank()) {
            return Optional.empty();
        }

        String normalized_path = video_path.trim().replace('\\', '/');
        while (normalized_path.startsWith("/")) {
            normalized_path = normalized_path.substring(1);
        }
        if (normalized_path.isBlank() || normalized_path.contains(":")) {
            return Optional.empty();
        }

        Path path = Path.of(normalized_path).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            return Optional.empty();
        }
        return Optional.of(path.toString().replace('\\', '/'));
    }

    // 返回客户端配置资源包中的固定视频根目录
    private static Path videoRoot() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(QuestEnhance.MOD_ID)
                .resolve("assets")
                .resolve(QuestEnhance.MOD_ID)
                .resolve("videos")
                .resolve("ftb")
                .toAbsolutePath()
                .normalize();
    }
}
