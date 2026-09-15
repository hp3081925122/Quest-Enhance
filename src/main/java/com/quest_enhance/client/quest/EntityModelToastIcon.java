package com.quest_enhance.client.quest;

import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// 为任务完成通知提供可直接绘制的生物模型图标
public final class EntityModelToastIcon extends Icon {
    private final ResourceLocation entity_id;
    private final Icon fallback;
    private final KillTaskEntityPreview entity_preview = new KillTaskEntityPreview();

    public EntityModelToastIcon(ResourceLocation entity_id, Icon fallback) {
        this.entity_id = entity_id;
        this.fallback = fallback;
    }

    @Override
    public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!this.entity_preview.render(this.entity_id, graphics, x, y, width, height, false)) {
            this.fallback.draw(graphics, x, y, width, height);
        }
    }
}
