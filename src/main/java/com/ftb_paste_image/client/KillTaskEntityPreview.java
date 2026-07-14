package com.ftb_paste_image.client;

import com.ftb_paste_image.FtbPasteImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class KillTaskEntityPreview {
    private ResourceLocation cached_entity_id;
    private ClientLevel cached_level;
    private LivingEntity cached_entity;

    // 创建并缓存预览实体，然后按指定图标区域自动缩放绘制
    public boolean render(
            ResourceLocation entity_id,
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return false;
        }

        // 实体注册名或世界变化时重新创建一次预览实体
        if (!entity_id.equals(this.cached_entity_id) || level != this.cached_level) {
            this.cached_entity_id = entity_id;
            this.cached_level = level;
            this.cached_entity = null;
            try {
                EntityType<?> entity_type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity_id).orElse(null);
                Entity entity = entity_type == null ? null : entity_type.create(level);
                if (entity instanceof LivingEntity living_entity) {
                    this.cached_entity = living_entity;
                }
            } catch (RuntimeException exception) {
                FtbPasteImage.LOGGER.error("Failed to create entity preview {}", entity_id, exception);
            }
        }

        if (this.cached_entity == null) {
            return false;
        }

        // 根据实体碰撞箱计算缩放并限制在按钮区域内
        float scale_by_width = width * 0.8F / Math.max(this.cached_entity.getBbWidth(), 0.25F);
        float scale_by_height = height * 0.85F / Math.max(this.cached_entity.getBbHeight(), 0.25F);
        int scale = Math.max(1, (int) Math.floor(Math.min(scale_by_width, scale_by_height)));

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(
                    graphics,
                    x + width / 2,
                    y + height,
                    scale,
                    0.35F,
                    -0.1F,
                    this.cached_entity
            );
            return true;
        } catch (RuntimeException exception) {
            this.cached_entity = null;
            FtbPasteImage.LOGGER.error("Failed to render entity preview {}", entity_id, exception);
            return false;
        } finally {
            graphics.disableScissor();
        }
    }
}
