package com.quest_enhance.client.background;

import dev.ftb.mods.ftblibrary.config.ImageResourceConfig;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectImageResourceScreen;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.ScreenWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

// 复用 FTB 图片资源选择器，并统一处理确认、取消和返回任务书界面
public final class BackgroundImagePicker {
    private BackgroundImagePicker() {
    }

    // 打开图片选择器并在确认后返回图片资源，取消时只关闭选择器
    public static void open(BaseScreen parent, ResourceLocation current, Consumer<ResourceLocation> onAccepted) {
        ImageResourceConfig config = new ImageResourceConfig();
        config.withAllowEmpty(true);
        config.setCurrentValue(current == null ? ImageResourceConfig.NONE : current);
        new SelectImageResourceScreen(config, changed -> {
            if (changed) {
                ResourceLocation resource_location = config.getValue();
                onAccepted.accept(ImageResourceConfig.NONE.equals(resource_location) ? null : resource_location);
            }

            if (Minecraft.getInstance().screen instanceof ScreenWrapper screen_wrapper) {
                screen_wrapper.getGui().closeGui(true);
            }
        }).withGridSize(8, 12).openGui();
    }
}
