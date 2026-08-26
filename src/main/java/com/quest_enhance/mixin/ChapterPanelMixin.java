package com.quest_enhance.mixin;

import com.quest_enhance.common.ChapterSidebarBackground;
import dev.ftb.mods.ftblibrary.client.gui.theme.Theme;
import dev.ftb.mods.ftblibrary.client.icon.IconHelper;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

// 在 FTB 章节面板背景层绘制任务书章节侧边栏背景图片
@Mixin(value = ChapterPanel.class, remap = false)
public abstract class ChapterPanelMixin {
    @Shadow
    @Final
    private QuestScreen questScreen;

    @Unique
    private static final Map<Identifier, Icon<?>> quest_enhance$background_icons = new HashMap<>();

    // 在原版侧边栏底色绘制完成后绘制自定义图片
    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void quest_enhance$draw_sidebar_background(
            GuiGraphicsExtractor graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        BaseQuestFile file = ((QuestScreenAccessor) (Object) this.questScreen).quest_enhance$get_file();
        ChapterSidebarBackground.get(file).ifPresent(resource_id -> {
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
