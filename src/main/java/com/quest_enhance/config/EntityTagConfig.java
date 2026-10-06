package com.quest_enhance.config;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class EntityTagConfig extends EnumConfig<String> {
    public EntityTagConfig(NameMap<String> name_map) {
        super(name_map);
    }

    @Override
    public void onClicked(Widget widget, MouseButton mouse_button, ConfigCallback callback) {
        if (!getCanEdit()) {
            return;
        }

        try {
            Class<?> screen_class = Class.forName(
                    "dev.ftb.mods.ftblibrary.config.EnumConfig$1"
            );
            Constructor<?> constructor = screen_class.getDeclaredConstructor(
                    EnumConfig.class,
                    ConfigCallback.class
            );
            constructor.setAccessible(true);
            Object screen = constructor.newInstance(this, callback);

            Method set_search_box = screen_class.getMethod(
                    "setHasSearchBox",
                    boolean.class
            );
            set_search_box.invoke(screen, true);

            Method open_gui = screen_class.getMethod("openGui");
            open_gui.invoke(screen);
        } catch (ReflectiveOperationException exception) {
            super.onClicked(widget, mouse_button, callback);
        }
    }
}
