package com.quest_enhance.common.description;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class PlayerPersistentDataDescription {
    public static final String MARKER_KEY = "quest_enhance.player_persistent_data";
    public static final int MAXIMUM_KEY_LENGTH = 128;
    public static final Pattern DATA_KEY = Pattern.compile("[^\\s{}]{1," + MAXIMUM_KEY_LENGTH + "}");

    private PlayerPersistentDataDescription() {
    }

    // 生成供任务描述识别的原版翻译组件 JSON 标记
    public static String create(String key, boolean i18n) {
        if (!isValidKey(key)) {
            throw new IllegalArgumentException("key is invalid");
        }

        JsonArray arguments = new JsonArray();
        arguments.add("key:" + key);
        arguments.add("i18n:" + i18n);
        JsonObject component = new JsonObject();
        component.addProperty("translate", MARKER_KEY);
        component.add("with", arguments);
        return component.toString();
    }

    // 从已解析的文字组件中读取持久化数据占位配置
    public static Optional<Placeholder> get(Component component) {
        if (!(component.getContents() instanceof TranslatableContents contents)
                || !MARKER_KEY.equals(contents.getKey())) {
            return Optional.empty();
        }

        Object[] arguments = contents.getArgs();
        String key = null;
        boolean i18n = false;
        for (Object argument : arguments) {
            String value = argumentText(argument);
            if (value.startsWith("key:")) {
                key = value.substring("key:".length());
            } else if (value.startsWith("i18n:")) {
                i18n = Boolean.parseBoolean(value.substring("i18n:".length()));
            }
        }
        if (!isValidKey(key)) {
            return Optional.empty();
        }
        return Optional.of(new Placeholder(key, i18n));
    }

    // 收集当前任务描述请求服务端数据时真正需要的全部键
    public static Set<String> findKeys(Collection<Component> components) {
        Set<String> keys = new LinkedHashSet<>();
        for (Component component : components) {
            findKeys(component, keys);
        }
        return keys;
    }

    // 校验网络请求和描述组件共用的数据键格式
    public static boolean isValidKey(String key) {
        return key != null && DATA_KEY.matcher(key).matches();
    }

    // 递归收集文字组件及其子组件中的数据键
    private static void findKeys(Component component, Set<String> keys) {
        get(component).ifPresent(placeholder -> keys.add(placeholder.key()));
        component.getSiblings().forEach(sibling -> findKeys(sibling, keys));
    }

    // 将翻译参数统一转换为可识别的字符串
    private static String argumentText(Object argument) {
        return argument instanceof Component component ? component.getString() : String.valueOf(argument);
    }

    public record Placeholder(String key, boolean i18n) {
    }
}
