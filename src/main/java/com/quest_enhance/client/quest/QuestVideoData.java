package com.quest_enhance.client.quest;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.common.canvas.ChapterCanvasData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class QuestVideoData {
    private static final String VIDEO_TAG = "quest_enhance_video";
    private static final String PLACEHOLDER_TAG = "quest_enhance_video_placeholder";
    private static final String BUNDLED_VIDEO_PREFIX = "assets/";
    private static final String BUNDLED_VIDEO_DIRECTORY = "videos";

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

    // 将配置路径解析为本地视频文件，资源包视频会提取到临时缓存供 VLC 读取
    public static Optional<Path> resolve(String video_path) {
        Optional<String> normalized_path = normalize(video_path);
        if (normalized_path.isEmpty()) {
            return Optional.empty();
        }

        if (normalized_path.get().startsWith(BUNDLED_VIDEO_PREFIX)) {
            return resolveBundledVideo(normalized_path.get());
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

    // 扫描配置目录和模组资源包，并返回可直接写入任务配置的排序相对路径
    public static List<String> listAvailableVideos() {
        Set<String> videos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Path video_root = videoRoot();
        if (Files.isDirectory(video_root)) {
            try (var files = Files.walk(video_root)) {
                files
                        .filter(Files::isRegularFile)
                        .filter(path -> isVideoFile(path.getFileName().toString()))
                        .map(video_root::relativize)
                        .map(path -> path.toString().replace('\\', '/'))
                        .forEach(videos::add);
            } catch (IOException exception) {
                QuestEnhance.LOGGER.error("Failed to scan configured videos", exception);
            }
        }

        // 资源包视频用 assets 前缀与原有配置目录路径区分
        int configured_video_count = videos.size();
        List<String> bundled_videos = Minecraft.getInstance().getResourceManager()
                .listResources(BUNDLED_VIDEO_DIRECTORY, location -> location.getNamespace().equals(QuestEnhance.MOD_ID)
                        && !location.getPath().startsWith(BUNDLED_VIDEO_DIRECTORY + "/ftb/")
                        && isVideoFile(location.getPath()))
                .keySet()
                .stream()
                .map(ResourceLocation::getPath)
                .map(path -> BUNDLED_VIDEO_PREFIX + path.substring(BUNDLED_VIDEO_DIRECTORY.length() + 1))
                .toList();
        videos.addAll(bundled_videos);
        QuestEnhance.LOGGER.debug(
                "Scanned available videos: configured={}, bundled={}, total={}",
                configured_video_count,
                bundled_videos.size(),
                videos.size()
        );
        return new ArrayList<>(videos);
    }

    // 统一保存为使用正斜杠的可分发相对路径
    public static Optional<String> normalize(String video_path) {
        return ChapterCanvasData.normalizeVideoPath(video_path);
    }

    // 从当前资源包读取模组内视频，并缓存为 VLC 可访问的真实文件
    private static Optional<Path> resolveBundledVideo(String video_path) {
        String relative_path = video_path.substring(BUNDLED_VIDEO_PREFIX.length());
        ResourceLocation resource_location = ResourceLocation.fromNamespaceAndPath(
                QuestEnhance.MOD_ID,
                BUNDLED_VIDEO_DIRECTORY + "/" + relative_path
        );
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(resource_location);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        String extension = relative_path.contains(".")
                ? relative_path.substring(relative_path.lastIndexOf('.'))
                : "";
        Path cache_path = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("quest_enhance_resource_videos")
                .resolve(UUID.nameUUIDFromBytes(video_path.getBytes(StandardCharsets.UTF_8)) + extension)
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(cache_path.getParent());
            if (!Files.isRegularFile(cache_path)) {
                try (InputStream input_stream = resource.get().open()) {
                    Files.copy(input_stream, cache_path, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            cache_path.toFile().deleteOnExit();
            return Optional.of(cache_path);
        } catch (IOException exception) {
            QuestEnhance.LOGGER.error("Failed to extract bundled video resource {}", resource_location, exception);
            return Optional.empty();
        }
    }

    // 仅允许播放器后端支持的常见本地视频封装格式
    private static boolean isVideoFile(String file_name) {
        String name = file_name.toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4")
                || name.endsWith(".webm")
                || name.endsWith(".mkv")
                || name.endsWith(".mov")
                || name.endsWith(".avi")
                || name.endsWith(".m4v")
                || name.endsWith(".ogv");
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
