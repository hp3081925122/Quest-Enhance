package com.quest_enhance.client.background;

import dev.ftb.mods.ftblibrary.client.config.editable.EditableImageResource;
import dev.ftb.mods.ftblibrary.client.config.gui.resource.SelectImageResourceScreen;
import dev.ftb.mods.ftblibrary.client.gui.widget.BaseScreen;
import dev.ftb.mods.ftblibrary.client.gui.widget.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

// 复用 FTB 图片资源选择器，并统一处理确认、取消和返回任务书界面
public final class BackgroundImagePicker {
    private BackgroundImagePicker() {
    }

    // 打开图片选择器并在确认后返回图片资源，取消时只关闭选择器
    public static void open(BaseScreen parent, Identifier current, Consumer<Identifier> onAccepted) {
        EditableImageResource config = new EditableImageResource();
        config.withAllowEmpty(true);
        config.setValue(current == null ? EditableImageResource.NONE : current);
        new SelectImageResourceScreen(config, changed -> {
            if (changed) {
                Identifier resource_id = config.getValue();
                onAccepted.accept(EditableImageResource.NONE.equals(resource_id) ? null : resource_id);
            }

            if (Minecraft.getInstance().screen instanceof ScreenWrapper screen_wrapper) {
                screen_wrapper.getGui().closeGui(true);
            }
        }).withGridSize(8, 12).openGui();
    }
}
