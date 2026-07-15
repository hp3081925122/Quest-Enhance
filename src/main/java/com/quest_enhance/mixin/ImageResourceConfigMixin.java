package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.QuestDescriptionWidthContext;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.ImageResourceConfig;
import dev.ftb.mods.ftblibrary.config.IntConfig;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectableResource;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;

@Mixin(value = ImageResourceConfig.class, remap = false)
public abstract class ImageResourceConfigMixin {
    // 在 FTB Quests 原生选择器选中图片后同步更新同组宽高配置
    @Inject(method = "setResource", at = @At("RETURN"))
    private void quest_enhance$apply_native_image_size(
            SelectableResource<ResourceLocation> selected_resource,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (!callback_info.getReturnValue()) {
            return;
        }

        // 只处理带有图片、宽度、高度和自适应字段的 FTB Quests 图片配置组
        ImageResourceConfig image_config = (ImageResourceConfig) (Object) this;
        ConfigGroup group = image_config.getGroup();
        if (group == null || !"image".equals(image_config.id) || !group.getId().startsWith("ftbquests")) {
            return;
        }

        IntConfig width_config = null;
        IntConfig height_config = null;
        boolean has_fit_config = false;
        for (ConfigValue<?> value : group.getValues()) {
            if (value instanceof IntConfig int_config && "width".equals(value.id)) {
                width_config = int_config;
            } else if (value instanceof IntConfig int_config && "height".equals(value.id)) {
                height_config = int_config;
            } else if ("fit".equals(value.id)) {
                has_fit_config = true;
            }
        }

        int content_width = QuestDescriptionWidthContext.getActiveContentWidth();
        if (width_config == null || height_config == null || !has_fit_config || content_width <= 0) {
            return;
        }

        // 从当前客户端资源包读取图片原始尺寸并更新配置界面中的数值
        ResourceLocation image_location = selected_resource.resource();
        try {
            Resource resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(image_location)
                    .orElseThrow(() -> new IOException("Image resource was not found: " + image_location));
            try (InputStream input_stream = resource.open(); NativeImage native_image = NativeImage.read(input_stream)) {
                width_config.setValue(content_width);
                height_config.setValue(QuestDescriptionWidthContext.calculateHeight(
                        content_width,
                        native_image.getWidth(),
                        native_image.getHeight()
                ));
            }
        } catch (IOException | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to read selected FTB Quests image dimensions {}", image_location, exception);
        }
    }
}
