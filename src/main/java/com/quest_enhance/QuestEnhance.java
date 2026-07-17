package com.quest_enhance;

import com.quest_enhance.client.QuestEnhanceClientConfig;
import com.quest_enhance.client.QuestDescriptionTable;
import com.quest_enhance.client.QuestDescriptionVideo;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

// 提供模组标识和统一日志入口
@Mod(QuestEnhance.MOD_ID)
public final class QuestEnhance {
    public static final String MOD_ID = "quest_enhance";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 注册客户端配置目录资源包事件
    public QuestEnhance(IEventBus mod_event_bus, ModContainer mod_container) {
        mod_container.registerConfig(
                ModConfig.Type.CLIENT,
                QuestEnhanceClientConfig.SPEC,
                MOD_ID + "-client.toml"
        );
        mod_event_bus.addListener(this::addPackFinder);

        // 只在客户端模组总线上注册任务描述视频解析器
        if (FMLEnvironment.dist == Dist.CLIENT) {
            mod_event_bus.addListener(QuestDescriptionVideo::clientSetup);
            mod_event_bus.addListener(QuestDescriptionTable::clientSetup);
        }
    }

    // 将配置目录中的图片作为必选客户端资源包加载
    private void addPackFinder(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        // 创建资源包目录和固定的资源包元数据
        Path pack_root = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
        Path image_directory = pack_root
                .resolve("assets")
                .resolve(MOD_ID)
                .resolve("textures")
                .resolve("ftb");
        Path video_directory = pack_root
                .resolve("assets")
                .resolve(MOD_ID)
                .resolve("videos")
                .resolve("ftb");
        try {
            Files.createDirectories(image_directory);
            Files.createDirectories(video_directory);
            Files.writeString(
                    pack_root.resolve("pack.mcmeta"),
                    "{\n  \"pack\": {\n    \"description\": {\n      \"translate\": \"pack.quest_enhance.description\"\n    },\n    \"pack_format\": 34\n  }\n}\n",
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            LOGGER.error("Failed to prepare the configured image resource pack", exception);
            return;
        }

        // 把配置目录资源包加入客户端资源仓库并保持启用
        event.addRepositorySource(pack_consumer -> {
            PackLocationInfo pack_location = new PackLocationInfo(
                    MOD_ID,
                    Component.translatable("pack.quest_enhance.name"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );
            Pack pack = Pack.readMetaAndCreate(
                    pack_location,
                    BuiltInPackSource.fromName(location -> new PathPackResources(location, pack_root)),
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false)
            );
            if (pack != null) {
                pack_consumer.accept(pack);
            }
        });
    }
}
