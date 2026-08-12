package com.quest_enhance.client.integration;

import com.quest_enhance.QuestEnhance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PonderIntegration {
    private static final String PONDER_INDEX_CLASS = "net.createmod.ponder.foundation.PonderIndex";
    private static final String PONDER_UI_CLASS = "net.createmod.ponder.foundation.ui.PonderUI";

    private PonderIntegration() {
    }

    // 按 The Ponderer 的实际场景索引列出当前注册表中确实可思索的物品。
    public static List<ResourceLocation> getAvailableItems() {
        if (!ModList.get().isLoaded("ponderer")) {
            return List.of();
        }
        try {
            Class<?> ponder_index_class = Class.forName(PONDER_INDEX_CLASS);
            Method get_scene_access = ponder_index_class.getMethod("getSceneAccess");
            Object scene_access = get_scene_access.invoke(null);
            Method scenes_exist = get_scene_access.getReturnType().getMethod(
                    "doScenesExistForId",
                    ResourceLocation.class
            );
            List<ResourceLocation> item_ids = new ArrayList<>();
            for (ResourceLocation item_id : BuiltInRegistries.ITEM.keySet()) {
                if (Boolean.TRUE.equals(scenes_exist.invoke(scene_access, item_id))) {
                    item_ids.add(item_id);
                }
            }
            item_ids.sort(Comparator.comparing(ResourceLocation::toString));
            return item_ids;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            QuestEnhance.LOGGER.debug("Failed to enumerate Ponder scenes", exception);
            return List.of();
        }
    }

    // 通过反射隔离可选的 The Ponderer/Ponder UI，未安装时不加载其客户端类。
    public static boolean open(ResourceLocation item_id) {
        if (!ModList.get().isLoaded("ponderer")) {
            showMessage("quest_enhance.description_component.ponder.missing");
            return false;
        }
        try {
            Class<?> ponder_ui_class = Class.forName(PONDER_UI_CLASS);
            Method factory = ponder_ui_class.getMethod("of", ResourceLocation.class);
            Object screen = factory.invoke(null, item_id);
            if (!(screen instanceof Screen ponder_screen)) {
                QuestEnhance.LOGGER.warn("The Ponderer returned an invalid Ponder screen for {}", item_id);
                return false;
            }
            Minecraft.getInstance().setScreen(ponder_screen);
            return true;
        } catch (ClassNotFoundException exception) {
            showMessage("quest_enhance.description_component.ponder.missing");
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            QuestEnhance.LOGGER.error("Failed to open a Ponder scene for {}", item_id, exception);
            showMessage("quest_enhance.description_component.ponder.error");
            return false;
        }
    }

    // 在客户端聊天栏中显示可选集成缺失或打开失败的提示。
    private static void showMessage(String translation_key) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(translation_key), false);
        }
    }
}
