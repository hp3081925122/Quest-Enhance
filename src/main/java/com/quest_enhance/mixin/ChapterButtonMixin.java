package com.quest_enhance.mixin;

import com.quest_enhance.client.background.BackgroundImagePicker;
import com.quest_enhance.common.ChapterBackground;
import dev.ftb.mods.ftblibrary.client.gui.theme.Theme;
import dev.ftb.mods.ftblibrary.client.gui.widget.BaseScreen;
import dev.ftb.mods.ftblibrary.client.gui.widget.ContextMenuItem;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.client.gui.ContextMenuBuilder;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

// 在章节右键菜单中加入章节背景图片设置入口
@Mixin(value = ChapterPanel.ChapterButton.class, remap = false)
public abstract class ChapterButtonMixin {
    @Shadow
    @Final
    private Chapter chapter;

    // 在原生章节菜单顶部加入背景图片选择器
    @Redirect(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/client/gui/ContextMenuBuilder;openContextMenu(Ldev/ftb/mods/ftblibrary/client/gui/widget/BaseScreen;)V"
            )
    )
    private void quest_enhance$open_chapter_context_menu(
            ContextMenuBuilder context_menu_builder,
            BaseScreen screen
    ) {
        context_menu_builder.insertAtTop(List.of(new ContextMenuItem(
                Component.translatable("quest_enhance.chapter_background"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        ChapterBackground.get(this.chapter).orElse(null),
                        resource_id -> {
                            ChapterBackground.set(this.chapter, resource_id);
                            EditObjectMessage.sendToServer(this.chapter);
                            ((QuestScreen) screen).refreshQuestPanel();
                        }
                )
        )));
        context_menu_builder.openContextMenu(screen);
    }
}
