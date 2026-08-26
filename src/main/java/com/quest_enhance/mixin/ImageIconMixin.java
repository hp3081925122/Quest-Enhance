package com.quest_enhance.mixin;

import com.quest_enhance.client.media.LocalImageAssets;
import dev.ftb.mods.ftblibrary.client.icon.ImageIconRenderer;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 在 FTB 绑定图片前恢复本模组本地剪贴板图片的动态纹理
@Mixin(value = ImageIconRenderer.class, remap = false)
public abstract class ImageIconMixin {
    // 在图片渲染前确保配置目录图片已经注册到客户端纹理管理器
    @Inject(
            method = "render(Ldev/ftb/mods/ftblibrary/icon/ImageIcon;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
            at = @At("HEAD")
    )
    private void quest_enhance$restore_local_image_texture(
            ImageIcon image,
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        LocalImageAssets.ensureRegistered(image.texture);
    }
}
