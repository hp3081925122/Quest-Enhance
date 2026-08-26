package com.quest_enhance.common.integration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

public final class KubeJSPersistentDataBridge {
    private static final String WITH_PERSISTENT_DATA = "dev.latvian.mods.kubejs.core.WithPersistentData";
    private static Method getter;
    private static boolean checked;

    private KubeJSPersistentDataBridge() {
    }

    // 通过反射读取可选 KubeJS 的独立持久化数据，未安装时返回空值。
    public static CompoundTag get(Player player) {
        if (!checked) {
            checked = true;
            try {
                getter = Class.forName(WITH_PERSISTENT_DATA)
                        .getMethod("kjs$getPersistentData");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                getter = null;
            }
        }

        if (getter == null || player == null) {
            return null;
        }

        try {
            Object data = getter.invoke(player);
            return data instanceof CompoundTag tag ? tag : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}


