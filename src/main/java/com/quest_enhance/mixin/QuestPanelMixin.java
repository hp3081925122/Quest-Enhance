package com.quest_enhance.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.quest_enhance.DecorativeAnchor;
import com.quest_enhance.DecorativeDependencyLines;
import com.quest_enhance.client.ChapterCanvasText;
import com.quest_enhance.client.ChapterCanvasVideo;
import com.quest_enhance.client.VideoSelectionScreen;
import com.quest_enhance.client.VideoSupport;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.config.ui.EditStringConfigOverlay;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPositionableButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.Movable;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Mixin(value = QuestPanel.class, remap = false)
public abstract class QuestPanelMixin {
    @Unique
    private static final ImageIcon quest_enhance$DECORATIVE_LINE_TEXTURE =
            (ImageIcon) Icon.getIcon("quest_enhance:textures/gui/decorative_line.png");

    @Shadow
    @Final
    private QuestScreen questScreen;

    @Shadow
    protected double questX;

    @Shadow
    protected double questY;

    // 在章节节点画布的空白处右键菜单中加入辅助点、文字和视频入口
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

        // 将原生图像项和增强项移动到菜单顶部，避免窗口化小分辨率裁掉底部选项
        int insert_index = 0;
        int image_index = TaskTypes.TYPES.size();
        if (image_index < context_menu.size()) {
            ContextMenuItem image_item = context_menu.remove(image_index);
            context_menu.add(insert_index++, image_item);
        }

        // 创建可参与多选、移动和删除的装饰线辅助点
        context_menu.add(insert_index++, new ContextMenuItem(
                Component.translatable("quest_enhance.decorative_anchor.add"),
                Icons.MARKER,
                button -> {
                    ChapterImage anchor = DecorativeAnchor.create(chapter, x, y);
                    chapter.addImage(anchor);
                    EditObjectMessage.sendToServer(chapter);
                    this.questScreen.refreshQuestPanel();
                }
        ));

        // 打开单行文字输入框，并在确认后创建原生章节图片对象
        ContextMenuItem text_item = new ContextMenuItem(
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
                                    EditObjectMessage.sendToServer(chapter);
                                }
                                this.questScreen.openGui();
                            },
                            Component.translatable("quest_enhance.chapter_text")
                    ).atMousePosition();
                    overlay.setWidth(180);
                    overlay.setExtraZlevel(600);
                    this.questScreen.pushModalPanel(overlay);
                }
        );
        context_menu.add(insert_index++, text_item);

        // 输入视频相对路径，并在确认后创建十六比九的章节背景对象
        if (VideoSupport.isAvailable()) {
            context_menu.add(insert_index, new ContextMenuItem(
                    Component.translatable("quest_enhance.chapter_video"),
                    Icons.CAMERA,
                    button -> VideoSelectionScreen.open(button.getParent(), "", false, video_path -> {
                        ChapterImage image = ChapterCanvasVideo.create(chapter, video_path, x, y);
                        chapter.addImage(image);
                        EditObjectMessage.sendToServer(chapter);
                        this.questScreen.refreshQuestPanel();
                    })
            ));
        }
        return context_menu;
    }

    // 在任务按钮绘制前连接任务和辅助点组成的有序折线路径
    @Inject(method = "drawOffsetBackground", at = @At("TAIL"))
    private void quest_enhance$draw_decorative_dependency_line(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        Chapter chapter = ((QuestScreenAccessor) (Object) this.questScreen)
                .quest_enhance$get_selected_chapter();
        if (chapter == null) {
            return;
        }

        // 建立任务编号和辅助点 UUID 到当前画布按钮的映射
        Map<String, Widget> node_buttons = new HashMap<>();
        Set<Widget> anchor_buttons = new HashSet<>();
        for (Widget widget : ((Panel) (Object) this).getWidgets()) {
            if (!(widget instanceof QuestPositionableButton positionable)) {
                continue;
            }
            Movable movable = positionable.moveAndDeleteFocus();
            if (movable instanceof Quest quest) {
                node_buttons.put(DecorativeDependencyLines.questNode(quest.getMovableID()), widget);
            } else if (movable instanceof ChapterImage image) {
                DecorativeAnchor.nodeKey(image).ifPresent(node_key -> {
                    node_buttons.put(node_key, widget);
                    anchor_buttons.add(widget);
                });
            }
        }

        // 按保存顺序连接节点，辅助点只改变路径而不显示为玩家可见内容
        for (DecorativeDependencyLines.Line line : DecorativeDependencyLines.get(chapter)) {
            List<Widget> line_buttons = new ArrayList<>(line.nodes().size());
            for (String node_key : line.nodes()) {
                Widget widget = node_buttons.get(node_key);
                if (widget != null) {
                    line_buttons.add(widget);
                }
            }
            if (line_buttons.size() < 2) {
                continue;
            }

            // 使用与原生依赖线相同的旋转纹理四边形连接相邻节点
            quest_enhance$DECORATIVE_LINE_TEXTURE.bindTexture();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            float half_width = 3.0F * this.questScreen.getZoom() / 16.0F;
            for (int index = 1; index < line_buttons.size(); index++) {
                Widget previous = line_buttons.get(index - 1);
                Widget current = line_buttons.get(index);
                int previous_x = previous.getX() + previous.getWidth() / 2;
                int previous_y = previous.getY() + previous.getHeight() / 2;
                int current_x = current.getX() + current.getWidth() / 2;
                int current_y = current.getY() + current.getHeight() / 2;
                float delta_x = current_x - previous_x;
                float delta_y = current_y - previous_y;
                float length = (float) Math.sqrt(delta_x * delta_x + delta_y * delta_y);
                if (length <= 0.0F) {
                    continue;
                }
                float start_offset = anchor_buttons.contains(previous)
                        ? Math.min(previous.getWidth(), previous.getHeight()) / 2.0F
                        : 0.0F;
                float end_offset = anchor_buttons.contains(current)
                        ? Math.min(current.getWidth(), current.getHeight()) / 2.0F
                        : 0.0F;
                int line_start_x = Math.round(previous_x + delta_x * start_offset / length);
                int line_start_y = Math.round(previous_y + delta_y * start_offset / length);
                int line_end_x = Math.round(current_x - delta_x * end_offset / length);
                int line_end_y = Math.round(current_y - delta_y * end_offset / length);
                quest_enhance$draw_textured_line(
                        graphics, line_start_x, line_start_y, line_end_x, line_end_y,
                        half_width, 0, 180, 180, 255
                );
            }
        }
    }

    // 沿两点方向平铺无箭头纹理，并保留原生依赖线的末端渐暗效果
    @Unique
    private static void quest_enhance$draw_textured_line(
            GuiGraphics graphics,
            int start_x,
            int start_y,
            int end_x,
            int end_y,
            float half_width,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        float delta_x = end_x - start_x;
        float delta_y = end_y - start_y;
        float length = (float) Math.sqrt(delta_x * delta_x + delta_y * delta_y);
        if (length <= 0.0F) {
            return;
        }

        // 将水平矩形旋转到两个节点的连线方向
        graphics.pose().pushPose();
        graphics.pose().translate(start_x, start_y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(delta_y, delta_x)));
        Matrix4f matrix = graphics.pose().last().pose();
        float max_u = length / half_width / 2.0F;
        int end_red = red * 3 / 4;
        int end_green = green * 3 / 4;
        int end_blue = blue * 3 / 4;

        // 按当前版本的 POSITION_TEX_COLOR 顶点格式提交纹理矩形
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
        );
        buffer.addVertex(matrix, 0.0F, -half_width, 0.0F).setUv(max_u, 0.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, 0.0F, half_width, 0.0F).setUv(max_u, 1.0F).setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, length, half_width, 0.0F).setUv(0.0F, 1.0F).setColor(end_red, end_green, end_blue, alpha);
        buffer.addVertex(matrix, length, -half_width, 0.0F).setUv(0.0F, 0.0F).setColor(end_red, end_green, end_blue, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        graphics.pose().popPose();
    }
}
