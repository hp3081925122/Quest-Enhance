package com.quest_enhance.client.media;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class VideoRenderHelper {
    private static GpuTextureView texture_view;
    private static int texture_id = -1;
    private static int texture_width;
    private static int texture_height;

    private VideoRenderHelper() {
    }

    // 将 WaterMedia 持有的 OpenGL 纹理包装为只借用的 Minecraft GPU 纹理后提交到 GUI 渲染队列
    public static void draw(
            GuiGraphicsExtractor graphics,
            long texture,
            int source_width,
            int source_height,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (texture <= 0L || texture > Integer.MAX_VALUE) {
            return;
        }

        int raw_texture_id = (int) texture;
        if (texture_view == null || texture_id != raw_texture_id || texture_width != source_width || texture_height != source_height) {
            release();
            texture_view = RenderSystem.getDevice().createTextureView(new BorrowedGlTexture(raw_texture_id, source_width, source_height));
            texture_id = raw_texture_id;
            texture_width = source_width;
            texture_height = source_height;
        }

        // WaterMedia 输出的纹理坐标与旧版手动四边形保持一致，避免画面上下颠倒
        graphics.blit(
                texture_view,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                left,
                top,
                right,
                bottom,
                0.0F,
                1.0F,
                0.0F,
                1.0F
        );
    }

    // 仅释放 Minecraft 创建的纹理视图，原始 OpenGL 纹理由 WaterMedia 自己管理
    public static void release() {
        if (texture_view != null) {
            texture_view.close();
            texture_view = null;
        }
        texture_id = -1;
        texture_width = 0;
        texture_height = 0;
    }

    private static final class BorrowedGlTexture extends GlTexture {
        private BorrowedGlTexture(int texture_id, int width, int height) {
            super(
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    "WaterMedia video texture",
                    TextureFormat.RGBA8,
                    width,
                    height,
                    1,
                    1,
                    texture_id
            );
        }

        @Override
        public void close() {
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
