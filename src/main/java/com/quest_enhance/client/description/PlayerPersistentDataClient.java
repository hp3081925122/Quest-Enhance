package com.quest_enhance.client.description;

import com.quest_enhance.common.description.PlayerPersistentDataDescription;
import com.quest_enhance.common.integration.KubeJSPersistentDataBridge;
import com.quest_enhance.common.network.QuestEnhanceNetwork;
import dev.ftb.mods.ftblibrary.ui.Panel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.lang.ref.WeakReference;

public final class PlayerPersistentDataClient {
    private static final long REQUEST_INTERVAL_MILLIS = 1000L;
    private static final CompoundTag SERVER_FORGE_DATA = new CompoundTag();
    private static final CompoundTag SERVER_KUBEJS_DATA = new CompoundTag();
    private static WeakReference<Panel> active_panel = new WeakReference<>(null);
    private static long last_request_time;

    private PlayerPersistentDataClient() {
    }

    // 打开任务详情时请求该页描述中引用的服务端数据。
    public static void request(Collection<Component> components, Panel panel) {
        active_panel = new WeakReference<>(panel);
        Set<String> keys = PlayerPersistentDataDescription.findKeys(components);
        long current_time = System.currentTimeMillis();
        if (keys.isEmpty() || current_time - last_request_time < REQUEST_INTERVAL_MILLIS) {
            return;
        }
        last_request_time = current_time;
        QuestEnhanceNetwork.requestPlayerPersistentData(keys);
    }

    // 按服务端 Forge、客户端 Forge、服务端 KubeJS、客户端 KubeJS 的优先级替换占位组件。
    public static Optional<Component> resolve(Component component) {
        Optional<PlayerPersistentDataDescription.Placeholder> placeholder = PlayerPersistentDataDescription.get(component);
        if (placeholder.isEmpty()) {
            return Optional.empty();
        }

        Tag value = findValue(placeholder.get().key());
        if (value == null) {
            return Optional.of(Component.empty());
        }

        String text = value.getAsString();
        Component resolved = placeholder.get().i18n()
                ? Component.translatableWithFallback(text, text)
                : Component.literal(text);
        return Optional.of(resolved.copy().withStyle(component.getStyle()));
    }

    // 更新本地服务端快照，数据变化时重建正在查看的任务说明。
    public static void receive(List<String> keys, CompoundTag forge_data, CompoundTag kubejs_data) {
        boolean changed = merge(SERVER_FORGE_DATA, keys, forge_data)
                | merge(SERVER_KUBEJS_DATA, keys, kubejs_data);
        Panel panel = active_panel.get();
        if (changed && panel != null) {
            panel.refreshWidgets();
        }
    }

    private static Tag findValue(String key) {
        Tag value = SERVER_FORGE_DATA.get(key);
        if (value != null) {
            return value;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        value = player.getPersistentData().get(key);
        if (value != null) {
            return value;
        }

        value = SERVER_KUBEJS_DATA.get(key);
        if (value != null) {
            return value;
        }

        CompoundTag kubejs_data = KubeJSPersistentDataBridge.get(player);
        return kubejs_data == null ? null : kubejs_data.get(key);
    }

    private static boolean merge(CompoundTag target, Collection<String> keys, CompoundTag source) {
        boolean changed = false;
        for (String key : keys) {
            Tag source_value = source.get(key);
            Tag target_value = target.get(key);
            if (source_value == null) {
                if (target_value != null) {
                    target.remove(key);
                    changed = true;
                }
            } else if (!source_value.equals(target_value)) {
                target.put(key, source_value.copy());
                changed = true;
            }
        }
        return changed;
    }
}
