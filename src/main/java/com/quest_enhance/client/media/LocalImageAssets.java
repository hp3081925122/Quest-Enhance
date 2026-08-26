package com.quest_enhance.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.quest_enhance.QuestEnhance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class LocalImageAssets {
    private static final String LOCAL_TEXTURE_PREFIX = "textures/ftb/";
    private static final Set<Identifier> REGISTERED_TEXTURES = new HashSet<>();
    private static final Set<Identifier> MISSING_TEXTURES = new HashSet<>();

    private LocalImageAssets() {
    }

    // 从本地配置目录恢复剪贴板图片对应的动态纹理
    public static void ensureRegistered(Identifier resource_id) {
        if (!isLocalImage(resource_id) || REGISTERED_TEXTURES.contains(resource_id)) {
            return;
        }

        Path asset_root = FMLPaths.CONFIGDIR.get()
                .resolve(QuestEnhance.MOD_ID)
                .resolve("assets")
                .resolve(QuestEnhance.MOD_ID)
                .toAbsolutePath()
                .normalize();
        Path image_path = asset_root.resolve(resource_id.getPath()).normalize();
        if (!image_path.startsWith(asset_root) || !Files.isRegularFile(image_path)) {
            if (MISSING_TEXTURES.add(resource_id)) {
                QuestEnhance.LOGGER.debug("Local image texture was not found: resource={}, path={}", resource_id, image_path);
            }
            return;
        }

        try (InputStream input_stream = Files.newInputStream(image_path)) {
            NativeImage native_image = NativeImage.read(input_stream);
            Minecraft.getInstance().getTextureManager().register(
                    resource_id,
                    new DynamicTexture(resource_id::toString, native_image)
            );
            REGISTERED_TEXTURES.add(resource_id);
            MISSING_TEXTURES.remove(resource_id);
            QuestEnhance.LOGGER.debug("Registered local image texture: resource={}, path={}", resource_id, image_path);
        } catch (IOException | RuntimeException exception) {
            if (MISSING_TEXTURES.add(resource_id)) {
                QuestEnhance.LOGGER.warn("Failed to register local image texture {}", resource_id, exception);
            }
        }
    }

    // 仅接管本模组剪贴板落盘的 PNG，避免干预资源包中的普通图片
    private static boolean isLocalImage(Identifier resource_id) {
        return resource_id != null
                && resource_id.getNamespace().equals(QuestEnhance.MOD_ID)
                && resource_id.getPath().startsWith(LOCAL_TEXTURE_PREFIX)
                && resource_id.getPath().endsWith(".png");
    }
}
