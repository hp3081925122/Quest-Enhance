package com.quest_enhance.client;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.network.chat.Component;

public final class VideoConfig extends ConfigValue<String> {
    @Override
    public Component getStringForGUI(String value) {
        return value == null || value.isBlank()
                ? Component.translatable("quest_enhance.video.none")
                : Component.literal(value);
    }

    @Override
    public Icon getIcon(String value) {
        return Icons.CAMERA;
    }

    // 点击配置项时始终打开完整视频列表，而不是循环切换枚举值
    @Override
    public void onClicked(Widget widget, MouseButton button, ConfigCallback callback) {
        if (!button.isLeft() || !this.getCanEdit()) {
            return;
        }

        VideoSelectionScreen.open(widget.getParent(), this.getValue(), true, selected_video -> {
            boolean changed = this.setCurrentValue(selected_video);
            callback.save(changed);
        });
    }
}
