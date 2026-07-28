package com.quest_enhance.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.quest_enhance.QuestEnhance;
import com.quest_enhance.mixin.ChapterImageAccessor;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChapterClipboardImage {
    private static final double MAXIMUM_SIZE = 8.0D;
    private static final double MINIMUM_SIZE = 0.25D;

    private ChapterClipboardImage() {
    }

    // 读取系统剪贴板并创建可保存到章节画布的图片对象
    public static ChapterImage paste(Chapter chapter, double x, double y, double quest_button_size) {
        BufferedImage buffered_image;
        try {
            buffered_image = WindowsClipboardImage.read();
            if (buffered_image == null) {
                return null;
            }
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to read a clipboard image for the chapter canvas", exception);
            return null;
        }

        // 将图片持久化到现有剪贴板资源目录，并立即注册动态纹理
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Path output_directory = FMLPaths.CONFIGDIR.get()
                    .resolve(QuestEnhance.MOD_ID)
                    .resolve("assets")
                    .resolve(QuestEnhance.MOD_ID)
                    .resolve("textures")
                    .resolve("ftb");
            Files.createDirectories(output_directory);

            String file_name = "clipboard_" + System.currentTimeMillis() + ".png";
            Path output_path = output_directory.resolve(file_name);
            if (!ImageIO.write(buffered_image, "png", output_path.toFile())) {
                throw new IOException("No PNG writer is available");
            }

            ResourceLocation resource_location = ResourceLocation.fromNamespaceAndPath(
                    QuestEnhance.MOD_ID,
                    "textures/ftb/" + file_name
            );
            try (InputStream input_stream = Files.newInputStream(output_path)) {
                NativeImage native_image = NativeImage.read(input_stream);
                minecraft.getTextureManager().register(resource_location, new DynamicTexture(native_image));
            }

            // 按原图比例换算画布尺寸，并限制最长边以避免截图占满画布
            double safe_button_size = Math.max(1.0D, quest_button_size);
            double width = buffered_image.getWidth() / safe_button_size;
            double height = buffered_image.getHeight() / safe_button_size;
            double scale = Math.min(1.0D, MAXIMUM_SIZE / Math.max(width, height));
            ChapterImage image = new ChapterImage(0L, chapter)
                    .setPosition(x, y)
                    .setImage(Icon.getIcon(resource_location));
            ChapterImageAccessor accessor = (ChapterImageAccessor) (Object) image;
            accessor.quest_enhance$set_width(Math.max(MINIMUM_SIZE, width * scale));
            accessor.quest_enhance$set_height(Math.max(MINIMUM_SIZE, height * scale));
            QuestEnhance.LOGGER.info("Pasted clipboard image to chapter canvas: {}", output_path);
            return image;
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to paste a clipboard image to the chapter canvas", exception);
            return null;
        }
    }
}
