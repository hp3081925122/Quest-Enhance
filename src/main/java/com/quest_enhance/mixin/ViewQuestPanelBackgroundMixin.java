package com.quest_enhance.mixin;

import com.quest_enhance.common.QuestViewBackground;
import dev.ftb.mods.ftblibrary.client.gui.theme.Theme;
import dev.ftb.mods.ftblibrary.client.icon.IconHelper;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
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
    private static final Map<Identifier, Icon<?>> quest_enhance$background_icons = new HashMap<>();

    // 在原版详情背景之后绘制自定义图片
    @Inject(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/client/icon/IconHelper;renderIcon(Ldev/ftb/mods/ftblibrary/icon/Icon;Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void quest_enhance$draw_view_background(
            GuiGraphicsExtractor graphics,
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

        QuestViewBackground.get(this.quest).ifPresent(resource_id -> {
            Icon<?> background_icon = quest_enhance$background_icons.computeIfAbsent(
                    resource_id,
                    Icon::getIcon
            );
            if (!background_icon.isEmpty()) {
                IconHelper.renderIcon(background_icon, graphics, x, y, width, height);
            }
        });
    }
}
