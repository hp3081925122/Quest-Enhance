package com.quest_enhance.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = QuestPanel.class, remap = false)
public interface QuestPanelAccessor {
    // 调用 FTB 原生前置线绘制，保持控制点和主题纹理行为不变
    @Invoker("renderConnection")
    void quest_enhance$render_connection(
            QuestButton source,
            QuestButton dependency,
            PoseStack pose,
            float half_width,
            int red,
            int green,
            int blue,
            int start_alpha,
            int end_alpha,
            float texture_offset,
            Tesselator tesselator
    );
    // 检查当前是否处于画布拖动框选状态
    @Invoker("isDraggingSelectionBox")
    boolean quest_enhance$is_dragging_selection_box();
}
