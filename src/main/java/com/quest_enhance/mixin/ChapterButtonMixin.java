package com.quest_enhance.mixin;

import com.quest_enhance.ChapterBackground;
import com.quest_enhance.client.background.BackgroundImagePicker;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
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

@Mixin(value = ChapterPanel.ChapterButton.class, remap = false)
public abstract class ChapterButtonMixin {
    @Shadow
    @Final
    private Chapter chapter;

    // 在章节右键菜单中加入背景图片设置入口
    @Redirect(
            method = "onClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/client/gui/ContextMenuBuilder;openContextMenu(Ldev/ftb/mods/ftblibrary/ui/BaseScreen;)V"
            )
    )
    private void quest_enhance$open_chapter_context_menu(
            ContextMenuBuilder context_menu_builder,
            BaseScreen screen
    ) {
        // 使用 FTB 图片资源选择器，选择结果由章节数据保存并同步到服务端
        context_menu_builder.insertAtTop(List.of(new ContextMenuItem(
                Component.translatable("quest_enhance.chapter_background"),
                Icons.ART,
                button -> BackgroundImagePicker.open(
                        screen,
                        ChapterBackground.get(this.chapter).orElse(null),
                        resource_location -> {
                            ChapterBackground.set(this.chapter, resource_location);
                            new EditObjectMessage(this.chapter).sendToServer();
                            ((QuestScreen) screen).refreshQuestPanel();
                        }
                )
        )));
        screen.openContextMenu(context_menu_builder.build(screen));
    }
}
