package com.quest_enhance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

public final class VideoRenderHelper {
    private VideoRenderHelper() {
    }

    // 将 WaterMedia 提供的原始 OpenGL 纹理绘制到 GUI 矩形中
    public static void draw(GuiGraphics graphics, int texture, int left, int top, int right, int bottom) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // 按视频缓冲区方向设置纹理坐标，避免画面上下颠倒
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, left, top, 0.0F).uv(0.0F, 0.0F).endVertex();
        buffer.vertex(matrix, left, bottom, 0.0F).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, right, bottom, 0.0F).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(matrix, right, top, 0.0F).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
