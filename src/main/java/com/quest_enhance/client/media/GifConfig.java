package com.quest_enhance.client.media;

import dev.ftb.mods.ftblibrary.client.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
import dev.ftb.mods.ftblibrary.client.gui.input.MouseButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class GifConfig extends EditableConfigValue<Identifier> {
    @Override
    public Component getStringForGUI(Identifier value) {
        return value == null
                ? Component.translatable("quest_enhance.gif.none")
                : Component.literal(value.toString());
    }

    @Override
    public Icon getIcon(Identifier value) {
        return Icons.CAMERA;
    }

    // 点击 GIF 配置项时打开本模组的 GIF 资源选择列表
    @Override
    public void onClicked(Widget widget, MouseButton button, ConfigCallback callback) {
        if (!button.isLeft() || !this.getCanEdit()) {
            return;
        }

        GifSelectionScreen.open(widget.getParent(), this.getValue(), selected_gif -> {
            boolean changed = this.updateValue(selected_gif);
            callback.save(changed);
        });
    }
}
