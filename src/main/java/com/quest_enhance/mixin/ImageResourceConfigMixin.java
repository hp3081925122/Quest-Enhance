package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.description.QuestDescriptionWidthContext;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableConfigValue;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableImageResource;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableInt;
import dev.ftb.mods.ftblibrary.client.config.gui.resource.SelectableResource;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;

@Mixin(value = EditableImageResource.class, remap = false)
public abstract class ImageResourceConfigMixin {
    // 在 FTB Quests 原生选择器选中图片后同步更新同组宽高配置
    @Inject(method = "setResource", at = @At("RETURN"))
    private void quest_enhance$apply_native_image_size(
            SelectableResource<Identifier> selected_resource,
            CallbackInfoReturnable<Boolean> callback_info
    ) {
        if (!callback_info.getReturnValue()) {
            return;
        }

        // 只处理带有图片、宽度、高度和自适应字段的 FTB Quests 图片配置组
        EditableImageResource image_config = (EditableImageResource) (Object) this;
        EditableConfigGroup group = image_config.getGroup();
        if (group == null || !"image".equals(image_config.id) || !group.getId().startsWith("ftbquests")) {
            return;
        }

        EditableInt width_config = null;
        EditableInt height_config = null;
        boolean has_fit_config = false;
        for (EditableConfigValue<?> value : group.getValues()) {
            if (value instanceof EditableInt int_config && "width".equals(value.id)) {
                width_config = int_config;
            } else if (value instanceof EditableInt int_config && "height".equals(value.id)) {
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
        Identifier image_location = selected_resource.resource();
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
