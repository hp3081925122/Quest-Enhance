package com.quest_enhance.mixin;

import com.quest_enhance.common.QuestViewBackground;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

// 在任务详情面板原版背景上绘制每个任务独立的自定义背景图片
@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelBackgroundMixin {
    @Shadow
    private Quest quest;

    @Unique
    private static final Map<ResourceLocation, Icon> quest_enhance$background_icons = new HashMap<>();

    // 在原版详情背景之后、标题图标和边框之前绘制自定义图片
    @Inject(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/icon/Icon;draw(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void quest_enhance$draw_view_background(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        if (this.quest == null) {
            return;
        }

        QuestViewBackground.get(this.quest).ifPresent(resource_location -> {
            Icon background_icon = quest_enhance$background_icons.computeIfAbsent(
                    resource_location,
                    Icon::getIcon
            );
            if (!background_icon.isEmpty()) {
                background_icon.draw(graphics, x, y, width, height);
            }
        });
    }
}
