package com.quest_enhance.mixin;

import com.quest_enhance.client.ChapterCanvasText;
import com.quest_enhance.client.ChapterCanvasVideo;
import com.quest_enhance.client.VideoSelectionScreen;
import com.quest_enhance.client.VideoSupport;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.config.ui.EditStringConfigOverlay;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.regex.Pattern;

@Mixin(value = QuestPanel.class, remap = false)
public abstract class QuestPanelMixin {
    @Shadow
    @Final
    private QuestScreen questScreen;

    @Shadow
    protected double questX;

    @Shadow
    protected double questY;

    // 在章节节点画布的空白处右键菜单中加入文字和视频入口
    @ModifyArg(
            method = "mousePressed",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/client/gui/quests/QuestScreen;openContextMenu(Ljava/util/List;)Ldev/ftb/mods/ftblibrary/ui/ContextMenu;"
            ),
            index = 0
    )
    private List<ContextMenuItem> quest_enhance$add_chapter_text_menu(List<ContextMenuItem> context_menu) {
        double x = this.questX;
        double y = this.questY;
        Chapter chapter = ((QuestScreenAccessor) (Object) this.questScreen).quest_enhance$get_selected_chapter();

        // 打开单行文字输入框，并在确认后创建原生章节图片对象
        context_menu.add(new ContextMenuItem(
                Component.translatable("quest_enhance.chapter_text"),
                Icons.CHAT,
                button -> {
                    StringConfig config = new StringConfig(Pattern.compile(".+"));
                    EditStringConfigOverlay<String> overlay = new EditStringConfigOverlay<>(
                            this.questScreen,
                            config,
                            accepted -> {
                                String text = config.getValue();
                                if (accepted && text != null && !text.isBlank()) {
                                    ChapterImage image = ChapterCanvasText.create(
                                            chapter,
                                            text,
                                            x,
                                            y,
                                            this.questScreen.getQuestButtonSize(),
                                            this.questScreen.getTheme()
                                    );
                                    chapter.addImage(image);
                                    new EditObjectMessage(chapter).sendToServer();
                                }
                                this.questScreen.openGui();
                            },
                            Component.translatable("quest_enhance.chapter_text")
                    ).atMousePosition();
                    overlay.setWidth(180);
                    overlay.setExtraZlevel(600);
                    this.questScreen.pushModalPanel(overlay);
                }
        ));

        // 输入视频相对路径，并在确认后创建十六比九的章节背景对象
        if (VideoSupport.isAvailable()) {
            context_menu.add(new ContextMenuItem(
                    Component.translatable("quest_enhance.chapter_video"),
                    Icons.CAMERA,
                    button -> VideoSelectionScreen.open(button.getParent(), "", false, video_path -> {
                        ChapterImage image = ChapterCanvasVideo.create(chapter, video_path, x, y);
                        chapter.addImage(image);
                        new EditObjectMessage(chapter).sendToServer();
                        this.questScreen.refreshQuestPanel();
                    })
            ));
        }
        return context_menu;
    }
}
