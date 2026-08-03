package com.quest_enhance.mixin;

import com.quest_enhance.client.media.LocalImageAssets;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ImageIcon.class, remap = false)
public abstract class ImageIconMixin {
    @Shadow
    @Final
    public ResourceLocation texture;

    // 在 FTB 绑定图片前恢复本模组本地剪贴板图片的动态纹理
    @Inject(method = "bindTexture", at = @At("HEAD"))
    private void quest_enhance$restore_local_image_texture(CallbackInfo callbackInfo) {
        LocalImageAssets.ensureRegistered(this.texture);
    }
}
