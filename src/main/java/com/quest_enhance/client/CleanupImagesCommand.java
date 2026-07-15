package com.quest_enhance.client;

import com.quest_enhance.QuestEnhance;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@EventBusSubscriber(modid = QuestEnhance.MOD_ID, value = Dist.CLIENT)
public final class CleanupImagesCommand {
    private static final Pattern GENERATED_IMAGE_PATTERN = Pattern.compile("clipboard_[0-9]+\\.png");
    private static final Pattern IMAGE_REFERENCE_PATTERN = Pattern.compile(
            "quest_enhance:textures/ftb/(clipboard_[0-9]+\\.png)"
    );
    private static final Set<String> TEXT_FILE_EXTENSIONS = Set.of("snbt", "json", "txt", "toml");

    private CleanupImagesCommand() {
    }

    // 注册清理未使用任务图片的客户端指令
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("questenhance")
                        .then(Commands.literal("cleanup").executes(CleanupImagesCommand::cleanup))
        );
    }

    // 扫描全部 FTB Quests 数据后删除没有引用的本模组图片
    private static int cleanup(CommandContext<CommandSourceStack> context) {
        Path game_directory = FMLPaths.GAMEDIR.get();
        Path image_directory = FMLPaths.CONFIGDIR.get()
                .resolve(QuestEnhance.MOD_ID)
                .resolve("assets")
                .resolve(QuestEnhance.MOD_ID)
                .resolve("textures")
                .resolve("ftb");

        if (!Files.isDirectory(image_directory)) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("quest_enhance.command.cleanup.nothing"),
                    false
            );
            return 0;
        }

        // 收集配置、默认配置、本地数据和所有存档中的 FTB Quests 目录
        Set<Path> quest_roots = new LinkedHashSet<>(List.of(
                FMLPaths.CONFIGDIR.get().resolve("ftbquests").toAbsolutePath().normalize(),
                game_directory.resolve("defaultconfigs").resolve("ftbquests").toAbsolutePath().normalize(),
                game_directory.resolve("local").resolve("ftbquests").toAbsolutePath().normalize()
        ));

        Path saves_directory = game_directory.resolve("saves");
        try {
            if (Files.isDirectory(saves_directory)) {
                try (Stream<Path> directories = Files.find(
                        saves_directory,
                        5,
                        (path, attributes) -> attributes.isDirectory()
                                && path.getFileName() != null
                                && path.getFileName().toString().equalsIgnoreCase("ftbquests")
                )) {
                    directories.map(path -> path.toAbsolutePath().normalize()).forEach(quest_roots::add);
                }
            }
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to locate FTB Quests save directories", exception);
            context.getSource().sendFailure(Component.translatable("quest_enhance.command.cleanup.scan_directories_failed"));
            return 0;
        }

        // 从文本数据中收集所有由本模组生成的图片文件名引用
        Set<String> referenced_images = new LinkedHashSet<>();
        try {
            for (Path quest_root : quest_roots) {
                if (!Files.isDirectory(quest_root)) {
                    continue;
                }

                try (Stream<Path> files = Files.walk(quest_root)) {
                    Iterator<Path> iterator = files.iterator();
                    while (iterator.hasNext()) {
                        Path file = iterator.next();
                        if (!Files.isRegularFile(file) || file.getFileName() == null) {
                            continue;
                        }

                        String file_name = file.getFileName().toString();
                        int extension_index = file_name.lastIndexOf('.');
                        if (extension_index < 0
                                || !TEXT_FILE_EXTENSIONS.contains(file_name.substring(extension_index + 1).toLowerCase(Locale.ROOT))) {
                            continue;
                        }

                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        Matcher matcher = IMAGE_REFERENCE_PATTERN.matcher(content);
                        while (matcher.find()) {
                            referenced_images.add(matcher.group(1));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to scan FTB Quests image references", exception);
            context.getSource().sendFailure(Component.translatable("quest_enhance.command.cleanup.scan_references_failed"));
            return 0;
        }

        // 在开始删除前完整列出所有符合本模组命名规则的候选图片
        List<Path> generated_images;
        try (Stream<Path> files = Files.list(image_directory)) {
            generated_images = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> GENERATED_IMAGE_PATTERN.matcher(path.getFileName().toString()).matches())
                    .toList();
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to list generated clipboard images", exception);
            context.getSource().sendFailure(Component.translatable("quest_enhance.command.cleanup.read_directory_failed"));
            return 0;
        }

        // 删除未引用图片并释放当前客户端中的动态纹理
        int deleted_count = 0;
        int failed_count = 0;
        long released_bytes = 0L;
        Minecraft minecraft = Minecraft.getInstance();
        for (Path image_path : generated_images) {
            String file_name = image_path.getFileName().toString();
            if (referenced_images.contains(file_name)) {
                continue;
            }

            try {
                long image_size = Files.size(image_path);
                Files.delete(image_path);
                minecraft.getTextureManager().release(ResourceLocation.fromNamespaceAndPath(
                        QuestEnhance.MOD_ID,
                        "textures/ftb/" + file_name
                ));
                deleted_count++;
                released_bytes += image_size;
            } catch (IOException | RuntimeException exception) {
                failed_count++;
                QuestEnhance.LOGGER.error("Failed to delete unused clipboard image {}", image_path, exception);
            }
        }

        // 将清理结果反馈给执行指令的玩家
        int final_deleted_count = deleted_count;
        long final_released_bytes = released_bytes;
        String released_size = final_released_bytes >= 1024L * 1024L
                ? String.format(Locale.ROOT, "%.2f MiB", final_released_bytes / 1024.0D / 1024.0D)
                : String.format(Locale.ROOT, "%.2f KiB", final_released_bytes / 1024.0D);
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "quest_enhance.command.cleanup.success",
                        final_deleted_count,
                        released_size
                ),
                false
        );

        if (failed_count > 0) {
            context.getSource().sendFailure(Component.translatable(
                    "quest_enhance.command.cleanup.delete_failed",
                    failed_count
            ));
        }

        QuestEnhance.LOGGER.info(
                "Cleaned up {} unused clipboard images and released {} bytes; {} deletions failed",
                deleted_count,
                released_bytes,
                failed_count
        );
        return deleted_count;
    }
}
