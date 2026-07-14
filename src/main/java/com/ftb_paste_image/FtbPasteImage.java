package com.ftb_paste_image;

import com.ftb_paste_image.client.FtbPasteImageClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.resource.PathPackResources;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// 提供模组标识和统一日志入口
@Mod(FtbPasteImage.MOD_ID)
public final class FtbPasteImage {
    public static final String MOD_ID = "ftb_paste_image";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 注册客户端配置目录资源包事件
    public FtbPasteImage(FMLJavaModLoadingContext loading_context) {
        loading_context.registerConfig(
                ModConfig.Type.CLIENT,
                FtbPasteImageClientConfig.SPEC,
                MOD_ID + "-client.toml"
        );
        loading_context.getModEventBus().addListener(this::addPackFinder);
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
        try {
            Files.createDirectories(image_directory);
            Files.writeString(
                    pack_root.resolve("pack.mcmeta"),
                    "{\n  \"pack\": {\n    \"description\": {\n      \"translate\": \"pack.ftb_paste_image.description\"\n    },\n    \"pack_format\": 15\n  }\n}\n",
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            LOGGER.error("Failed to prepare the configured image resource pack", exception);
            return;
        }

        // 把配置目录资源包加入客户端资源仓库并保持启用
        event.addRepositorySource(pack_consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    MOD_ID,
                    Component.translatable("pack.ftb_paste_image.name"),
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
