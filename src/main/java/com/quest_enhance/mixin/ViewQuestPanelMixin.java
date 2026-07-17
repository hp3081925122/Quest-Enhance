package com.quest_enhance.mixin;

import com.quest_enhance.client.QuestDescriptionWidthContext;
import com.quest_enhance.client.DescriptionComponentMenu;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelMixin {
    @Shadow
    private Quest quest;

    @Shadow
    private BlankPanel panelText;

    // 右键编辑由快捷菜单生成的独立 JSON 组件时改用对应配置界面
    @Inject(method = "editDescLine0", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$edit_description_component(
            Widget clicked_widget,
            int line,
            @Nullable Object type,
            CallbackInfo callback_info
    ) {
        if (line < 0 || line >= this.quest.getRawDescription().size()) {
            return;
        }

        Panel panel = (Panel) (Object) this;
        String raw_text = this.quest.getRawDescription().get(line);
        Consumer<String> save = edited -> {
            this.quest.getRawDescription().set(line, edited);
            new EditObjectMessage(this.quest).sendToServer();
            panel.refreshWidgets();
        };

        // 物品和网络图片不能交给只支持纹理资源的原生图片编辑器
        if (type instanceof ImageComponent image_component) {
            String image_id = image_component.image.toString();
            boolean item_icon = image_id.startsWith("item:");
            boolean remote_image = image_id.startsWith("http://") || image_id.startsWith("https://");
            if (!item_icon && !remote_image) {
                return;
            }

            // 从 FTB 已解析组件中恢复可选悬停文字
            Component parsed = ClientTextComponentUtils.parse(raw_text);
            HoverEvent hover_event = parsed.getStyle().getHoverEvent();
            Component hover_component = hover_event == null
                    ? null
                    : hover_event.getValue(HoverEvent.Action.SHOW_TEXT);
            String hover_text = hover_component == null ? "" : hover_component.getString();
            if (item_icon) {
                DescriptionComponentMenu.editItemIcon(panel, image_component, hover_text, save);
            } else {
                DescriptionComponentMenu.editRemoteImage(panel, image_component, hover_text, save);
            }
            callback_info.cancel();
            return;
        }

        // 其他图片和未知内容继续使用 FTB 原编辑器
        if (type != null) {
            return;
        }

        boolean handled = DescriptionComponentMenu.edit(panel, raw_text, save);
        if (handled) {
            callback_info.cancel();
        }
    }

    // 在打开描述编辑器前保存节点介绍区域扣除边距后的实际宽度
    @Inject(method = "editDescription", at = @At("HEAD"))
    private void quest_enhance$capture_description_width(CallbackInfo callback_info) {
        QuestDescriptionWidthContext.capture(this.panelText.getWidth());
    }

    // 为节点介绍页的图片选择器绑定宽度，并只对新建图片默认开启自适应
    @Inject(method = "editImage", at = @At("HEAD"))
    private void quest_enhance$prepare_image_editor(
            int line,
            ImageComponent component,
            CallbackInfo callback_info
    ) {
        QuestDescriptionWidthContext.activate(this.panelText.getWidth());
        if (line == -1) {
            component.fit = true;
        }
    }
}
