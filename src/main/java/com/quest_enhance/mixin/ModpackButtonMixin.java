package com.quest_enhance.mixin;

import com.quest_enhance.client.background.BackgroundImagePicker;
import com.quest_enhance.common.ChapterSidebarBackground;
import dev.ftb.mods.ftblibrary.client.gui.input.MouseButton;
import dev.ftb.mods.ftblibrary.client.gui.widget.ContextMenuItem;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// 在任务书标题栏的右键菜单中提供章节侧边栏背景图片设置
@Mixin(value = ChapterPanel.ModpackButton.class, remap = false)
public abstract class ModpackButtonMixin {
    // 右键任务书标题栏时打开章节侧边栏背景图片菜单
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_sidebar_background_menu(
            MouseButton mouse_button,
            CallbackInfo callback_info
    ) {
        ChapterPanel chapter_panel = ((ChapterPanelListButtonAccessor) (Object) this)
                .quest_enhance$get_chapter_panel();
        QuestScreen screen = (QuestScreen) chapter_panel.getGui();
        ClientQuestFile file = ((QuestScreenAccessor) (Object) screen).quest_enhance$get_file();
        if (!file.canEdit() || !mouse_button.isRight()) {
            return;
        }

        screen.openContextMenu(List.of(new ContextMenuItem(
                Component.translatable("quest_enhance.chapter_sidebar_background"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        ChapterSidebarBackground.get(file).orElse(null),
                        resource_id -> {
                            ChapterSidebarBackground.set(file, resource_id);
                            EditObjectMessage.sendToServer((BaseQuestFile) file);
                            screen.refreshChapterPanel();
                        }
                )
        )));
        callback_info.cancel();
    }
}
