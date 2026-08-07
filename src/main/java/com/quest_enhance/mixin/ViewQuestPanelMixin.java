package com.quest_enhance.mixin;

import com.quest_enhance.client.description.DescriptionComponentMenu;
import com.quest_enhance.client.description.PlayerPersistentDataClient;
import com.quest_enhance.client.description.QuestDescriptionGif;
import com.quest_enhance.client.description.QuestDescriptionWidthContext;
import dev.ftb.mods.ftblibrary.ui.BlankPanel;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.util.client.ClientTextComponentUtils;
import dev.ftb.mods.ftblibrary.util.client.ImageComponent;
import dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(value = ViewQuestPanel.class, remap = false)
public abstract class ViewQuestPanelMixin {
    // 只识别标准 Markdown ATX 标题写法，避免误处理正文中的井号
    @Unique
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+)$");

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
            // GIF 必须始终通过本模组配置页保存，避免原生编辑器覆写为静态图片标记
            if (QuestDescriptionGif.getData(raw_text).isPresent()) {
                DescriptionComponentMenu.editGif(panel, raw_text, save);
                callback_info.cancel();
                return;
            }

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

    // 打开任务详情时预先请求当前描述需要的服务端持久化数据。
    @Inject(method = "addWidgets", at = @At("HEAD"))
    private void quest_enhance$request_player_persistent_data(CallbackInfo callback_info) {
        PlayerPersistentDataClient.request(this.quest.getDescription(), (Panel) (Object) this);
    }

    // 将任务描述中的 Markdown ATX 标题转换为不同字号的 FTB 文字控件，普通正文继续沿用原渲染逻辑
    @Redirect(
            method = "addDescriptionText",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/TextField;setText(Lnet/minecraft/network/chat/Component;)Ldev/ftb/mods/ftblibrary/ui/TextField;"
            )
    )
    private TextField quest_enhance$render_markdown_heading(TextField field, Component component) {
        Optional<Component> persistent_data = PlayerPersistentDataClient.resolve(component);
        if (persistent_data.isPresent()) {
            return field.setText(persistent_data.get());
        }

        Matcher matcher = MARKDOWN_HEADING.matcher(component.getString());
        if (!matcher.matches()) {
            return field.setText(component);
        }

        int level = matcher.group(1).length();
        float scale = switch (level) {
            case 1 -> 1.5F;
            case 2 -> 1.3F;
            case 3 -> 1.15F;
            case 4 -> 1.05F;
            default -> 1.0F;
        };
        int spacing = switch (level) {
            case 1 -> 18;
            case 2 -> 16;
            case 3 -> 14;
            case 4 -> 12;
            default -> 10;
        };
        int[] prefix_to_skip = {matcher.start(2)};
        MutableComponent heading = Component.empty();
        component.visit((style, text) -> {
            int skip = Math.min(prefix_to_skip[0], text.length());
            prefix_to_skip[0] -= skip;
            if (skip < text.length()) {
                heading.append(Component.literal(text.substring(skip)).withStyle(style.applyFormat(ChatFormatting.BOLD)));
            }
            return Optional.empty();
        }, component.getStyle());
        int max_width = Math.max(1, (int) (this.panelText.getWidth() / scale));
        return field.setMaxWidth(max_width)
                .setText(heading)
                .setScale(scale)
                .setSpacing(spacing)
                .resize(field.getGui().getTheme());
    }
}
