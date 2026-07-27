package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class GifConfig extends ConfigValue<ResourceLocation> {
    @Override
    public Component getStringForGUI(ResourceLocation value) {
        return value == null
                ? Component.translatable("quest_enhance.gif.none")
                : Component.literal(value.toString());
    }

    @Override
    public Icon getIcon(ResourceLocation value) {
        return Icons.CAMERA;
    }

    // 点击 GIF 配置项时打开本模组的 GIF 资源选择列表
    @Override
    public void onClicked(Widget widget, MouseButton button, ConfigCallback callback) {
        if (!button.isLeft() || !this.getCanEdit()) {
            return;
        }

        GifSelectionScreen.open(widget.getParent(), this.getValue(), selected_gif -> {
            boolean changed = this.setCurrentValue(selected_gif);
            callback.save(changed);
        });
    }
}
