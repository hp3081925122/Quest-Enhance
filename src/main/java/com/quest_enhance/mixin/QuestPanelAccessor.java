package com.quest_enhance.mixin;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = QuestPanel.class, remap = false)
public interface QuestPanelAccessor {
    // 调用 FTB 原生前置线绘制，保持控制点和主题纹理行为不变
    @Invoker("renderConnection")
    void quest_enhance$render_connection(
            GuiGraphicsExtractor graphics,
            Icon<?> dependency_line_texture,
            Widget source,
            QuestButton dependency,
            Matrix3x2fStack pose,
            float half_width,
            int red,
            int green,
            int blue,
            int start_alpha,
            int end_alpha,
            float texture_offset
    );
}
