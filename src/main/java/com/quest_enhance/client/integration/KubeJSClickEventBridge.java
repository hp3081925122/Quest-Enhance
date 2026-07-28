package com.quest_enhance.client.integration;

import com.quest_enhance.QuestEnhance;

import java.lang.reflect.Method;

public final class KubeJSClickEventBridge {
    private static final String EVENTS_CLASS = "com.quest_enhance.kubejs.QuestEnhanceKubeJSEvents";
    private static final String EVENT_CLASS = "com.quest_enhance.kubejs.QuestEnhanceTextClickEvent";
    private static boolean unavailableLogged;

    private KubeJSClickEventBridge() {
    }

    // 通过反射调用可选的 KubeJS 事件，避免未安装 KubeJS 时加载失败
    public static void dispatch(String text, String chapterId, double x, double y, double width, double height) {
        try {
            ClassLoader classLoader = KubeJSClickEventBridge.class.getClassLoader();
            Class<?> eventsClass = Class.forName(EVENTS_CLASS, true, classLoader);
            Class<?> eventClass = Class.forName(EVENT_CLASS, true, classLoader);
            Object eventHandler = eventsClass.getField("CLICK").get(null);
            Object event = eventClass.getConstructor(
                    String.class,
                    String.class,
                    double.class,
                    double.class,
                    double.class,
                    double.class
            ).newInstance(text, chapterId, x, y, width, height);

            for (Method method : eventHandler.getClass().getMethods()) {
                if (method.getName().equals("post") && method.getParameterCount() == 1) {
                    method.invoke(eventHandler, event);
                    QuestEnhance.LOGGER.debug("Dispatched KubeJS click event: chapter={}", chapterId);
                    return;
                }
            }

            logUnavailable("post method not found");
        } catch (ReflectiveOperationException | LinkageError exception) {
            logUnavailable(exception.getClass().getSimpleName());
        }
    }

    // 仅记录一次可选兼容不可用的原因，避免每次点击都输出日志
    private static void logUnavailable(String reason) {
        if (!unavailableLogged) {
            QuestEnhance.LOGGER.debug("KubeJS click event bridge unavailable: {}", reason);
            unavailableLogged = true;
        }
    }
}
