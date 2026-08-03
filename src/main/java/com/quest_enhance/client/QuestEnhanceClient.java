package com.quest_enhance.client;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.config.QuestEnhanceClientConfig;
import com.quest_enhance.client.description.QuestDescriptionGif;
import com.quest_enhance.client.description.QuestDescriptionTable;
import com.quest_enhance.client.description.QuestDescriptionVideo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.resource.PathPackResources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuestEnhanceClient {
    private QuestEnhanceClient() {
    }

    // 注册只属于客户端的配置、资源包和描述组件初始化。
    public static void init(FMLJavaModLoadingContext loading_context) {
        loading_context.registerConfig(
                ModConfig.Type.CLIENT,
                QuestEnhanceClientConfig.SPEC,
                QuestEnhance.MOD_ID + "-client.toml"
        );
        loading_context.getModEventBus().addListener(QuestEnhanceClient::addPackFinder);
        loading_context.getModEventBus().addListener(QuestDescriptionGif::clientSetup);
        loading_context.getModEventBus().addListener(QuestDescriptionVideo::clientSetup);
        loading_context.getModEventBus().addListener(QuestDescriptionTable::clientSetup);
    }

    // 将配置目录中的图片和视频作为必选客户端资源包加载。
    private static void addPackFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        Path pack_root = FMLPaths.CONFIGDIR.get().resolve(QuestEnhance.MOD_ID);
        Path image_directory = pack_root
                .resolve("assets")
                .resolve(QuestEnhance.MOD_ID)
                .resolve("textures")
                .resolve("ftb");
        Path video_directory = pack_root
                .resolve("assets")
                .resolve(QuestEnhance.MOD_ID)
                .resolve("videos")
                .resolve("ftb");
        try {
            Files.createDirectories(image_directory);
            Files.createDirectories(video_directory);
            Files.writeString(
                    pack_root.resolve("pack.mcmeta"),
                    "{\n  \"pack\": {\n    \"description\": {\n      \"translate\": \"pack.quest_enhance.description\"\n    },\n    \"pack_format\": 15\n  }\n}\n",
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            QuestEnhance.LOGGER.error("Failed to prepare the configured image resource pack", exception);
            return;
        }

        event.addRepositorySource(pack_consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    QuestEnhance.MOD_ID,
                    Component.translatable("pack.quest_enhance.name"),
                    true,
                    pack_id -> new PathPackResources(pack_id, true, pack_root),
                    PackType.CLIENT_RESOURCES,
                    Pack.Position.TOP,
                    PackSource.BUILT_IN
            );
            if (pack != null) {
                pack_consumer.accept(pack);
            }
        });
    }
}
