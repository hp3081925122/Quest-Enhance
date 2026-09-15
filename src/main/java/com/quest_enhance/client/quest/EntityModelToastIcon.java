package com.quest_enhance.client.quest;

import dev.ftb.mods.ftblibrary.client.icon.IconRenderer;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

// 为任务完成通知提供使用 26.1.2 图标渲染器的生物模型图标
public final class EntityModelToastIcon extends Icon<EntityModelToastIcon> {
    private static final IconRenderer<EntityModelToastIcon> RENDERER = new IconRenderer<>() {
        @Override
        public void render(
                EntityModelToastIcon icon,
                GuiGraphicsExtractor graphics,
                int x,
                int y,
                int width,
                int height
        ) {
            if (!icon.entity_preview.render(icon.entity_id, graphics, x, y, width, height, true)) {
                ((IconRenderer) icon.fallback.getRenderer()).render(
                        icon.fallback,
                        graphics,
                        x,
                        y,
                        width,
                        height
                );
            }
        }
    };

    private final Identifier entity_id;
    private final Icon<?> fallback;
    private final KillTaskEntityPreview entity_preview = new KillTaskEntityPreview();

    public EntityModelToastIcon(Identifier entity_id, Icon<?> fallback) {
        this.entity_id = entity_id;
        this.fallback = fallback;
    }

    @Override
    public IconRenderer<EntityModelToastIcon> getRenderer() {
        return RENDERER;
    }
}
