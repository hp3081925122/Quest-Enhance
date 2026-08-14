package com.quest_enhance.client.integration;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class PonderItemConfig extends ConfigValue<String> {
    @Override
    public Component getStringForGUI(String value) {
        ResourceLocation item_id = ResourceLocation.tryParse(value);
        if (item_id == null || !BuiltInRegistries.ITEM.containsKey(item_id)) {
            return Component.literal(value).withStyle(ChatFormatting.RED);
        }

        Item item = BuiltInRegistries.ITEM.get(item_id);
        return item.getDefaultInstance().getHoverName()
                .copy()
                .append(Component.literal(" (" + item_id + ")").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Icon getIcon(String value) {
        return Icons.BOOK;
    }

    // 点击思索物品配置时打开可搜索的可思索物品列表。
    @Override
    public void onClicked(Widget widget, MouseButton button, ConfigCallback callback) {
        if (!button.isLeft() || !this.getCanEdit()) {
            return;
        }

        PonderItemSelectionScreen.open(widget.getParent(), this.getValue(), selected_item -> {
            boolean changed = this.setCurrentValue(selected_item.toString());
            callback.save(changed);
        });
    }
}
