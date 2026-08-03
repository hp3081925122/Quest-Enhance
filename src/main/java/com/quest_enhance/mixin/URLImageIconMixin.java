package com.quest_enhance.mixin;

import dev.ftb.mods.ftblibrary.icon.URLImageIcon;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = URLImageIcon.class, remap = false)
public abstract class URLImageIconMixin {
    // 查询远程纹理时禁止自动注册错误的资源包纹理
    @Redirect(
            method = "bindTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/texture/AbstractTexture;",
                    remap = true
            )
    )
    private AbstractTexture quest_enhance$get_remote_texture(
            TextureManager textureManager,
            ResourceLocation texture
    ) {
        return textureManager.getTexture(texture, null);
    }
}
