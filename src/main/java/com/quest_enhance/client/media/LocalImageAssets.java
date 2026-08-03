package com.quest_enhance.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.quest_enhance.QuestEnhance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class LocalImageAssets {
    private static final String LOCAL_TEXTURE_PREFIX = "textures/ftb/";
    private static final Set<ResourceLocation> REGISTERED_TEXTURES = new HashSet<>();
    private static final Set<ResourceLocation> MISSING_TEXTURES = new HashSet<>();

    private LocalImageAssets() {
    }

    // 从本地配置目录恢复剪贴板图片对应的动态纹理
    public static void ensureRegistered(ResourceLocation resourceLocation) {
        if (!isLocalImage(resourceLocation) || REGISTERED_TEXTURES.contains(resourceLocation)) {
            return;
        }

        Path assetRoot = FMLPaths.CONFIGDIR.get()
                .resolve(QuestEnhance.MOD_ID)
                .resolve("assets")
                .resolve(QuestEnhance.MOD_ID)
                .toAbsolutePath()
                .normalize();
        Path imagePath = assetRoot.resolve(resourceLocation.getPath()).normalize();
        if (!imagePath.startsWith(assetRoot) || !Files.isRegularFile(imagePath)) {
            if (MISSING_TEXTURES.add(resourceLocation)) {
                QuestEnhance.LOGGER.debug("Local image texture was not found: resource={}, path={}", resourceLocation, imagePath);
            }
            return;
        }

        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            NativeImage nativeImage = NativeImage.read(inputStream);
            Minecraft.getInstance().getTextureManager().register(resourceLocation, new DynamicTexture(nativeImage));
            REGISTERED_TEXTURES.add(resourceLocation);
            MISSING_TEXTURES.remove(resourceLocation);
            QuestEnhance.LOGGER.debug("Registered local image texture: resource={}, path={}", resourceLocation, imagePath);
        } catch (IOException | RuntimeException exception) {
            if (MISSING_TEXTURES.add(resourceLocation)) {
                QuestEnhance.LOGGER.warn("Failed to register local image texture {}", resourceLocation, exception);
            }
        }
    }

    // 仅接管本模组剪贴板落盘的 PNG，避免干预资源包中的普通图片
    private static boolean isLocalImage(ResourceLocation resourceLocation) {
        return resourceLocation.getNamespace().equals(QuestEnhance.MOD_ID)
                && resourceLocation.getPath().startsWith(LOCAL_TEXTURE_PREFIX)
                && resourceLocation.getPath().endsWith(".png");
    }
}
