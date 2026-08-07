package com.quest_enhance.mixin;

import com.quest_enhance.common.ChapterBackground;
import dev.ftb.mods.ftblibrary.config.ImageResourceConfig;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectImageResourceScreen;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import dev.ftb.mods.ftbquests.client.gui.ContextMenuBuilder;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
                button -> {
                    ImageResourceConfig config = new ImageResourceConfig();
                    config.withAllowEmpty(true);
                    config.setCurrentValue(
                            ChapterBackground.get(this.chapter).orElse(ImageResourceConfig.NONE)
                    );
                    new SelectImageResourceScreen(config, changed -> {
                        // FTB 回调参数表示资源值是否发生变化，而不是是否点击确认
                        if (changed) {
                            ResourceLocation resource_location = config.getValue();
                            ChapterBackground.set(
                                    this.chapter,
                                    ImageResourceConfig.NONE.equals(resource_location) ? null : resource_location
                            );
                            EditObjectMessage.sendToServer(this.chapter);
                            ((QuestScreen) screen).refreshQuestPanel();
                        }

                        // 选择器回调结束后返回原任务书界面
                        if (Minecraft.getInstance().screen instanceof ScreenWrapper screen_wrapper) {
                            screen_wrapper.getGui().closeGui(true);
                        }
                    }).withGridSize(8, 12).openGui();
                }
        )));
        screen.openContextMenu(context_menu_builder.build(screen));
    }
}
