package com.quest_enhance.client.integration;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Consumer;

public final class PonderItemSelectionScreen extends AbstractButtonListScreen {
    private final Panel parent_panel;
    private final ResourceLocation current_item;
    private final Consumer<ResourceLocation> selection_callback;
    private final List<ResourceLocation> ponder_items;

    private PonderItemSelectionScreen(
            Panel parent_panel,
            String current_item,
            Consumer<ResourceLocation> selection_callback
    ) {
        this.parent_panel = parent_panel;
        this.current_item = ResourceLocation.tryParse(current_item);
        this.selection_callback = selection_callback;
        this.ponder_items = PonderIntegration.getAvailableItems();
        this.setTitle(Component.translatable("quest_enhance.description_component.ponder.select"));
        this.setHasSearchBox(true);
    }

    // 使用 FTB 原生可搜索列表，只显示实际注册了思索场景的物品。
    public static void open(
            Panel parent_panel,
            String current_item,
            Consumer<ResourceLocation> selection_callback
    ) {
        new PonderItemSelectionScreen(parent_panel, current_item, selection_callback).openGui();
    }

    @Override
    public void addButtons(Panel panel) {
        if (this.ponder_items.isEmpty()) {
            panel.add(SimpleTextButton.create(
                    panel,
                    Component.translatable("quest_enhance.description_component.ponder.empty"),
                    Icons.INFO,
                    button -> {
                    }
            ));
            return;
        }

        for (ResourceLocation item_id : this.ponder_items) {
            Item item = BuiltInRegistries.ITEM.get(item_id);
            Component name = item.getDefaultInstance().getHoverName()
                    .copy()
                    .append(Component.literal(" (" + item_id + ")").withStyle(ChatFormatting.DARK_GRAY));
            Component label = item_id.equals(this.current_item)
                    ? Component.translatable("quest_enhance.description_component.ponder.selected", name)
                    : name;
            panel.add(SimpleTextButton.create(
                    panel,
                    label,
                    ItemIcon.getItemIcon(item),
                    button -> {
                        this.parent_panel.run();
                        this.selection_callback.accept(item_id);
                    }
            ));
        }
    }

    @Override
    public boolean onInit() {
        int target_width = Mth.clamp(460, 176, this.getWindow().getGuiScaledWidth() * 3 / 4);
        int target_height = Mth.clamp(
                this.ponder_items.size() * 20 + 50,
                166,
                this.getWindow().getGuiScaledHeight() * 4 / 5
        );
        this.setSize(target_width, target_height);
        return super.onInit();
    }

    @Override
    public void onBack() {
        this.parent_panel.run();
    }

    @Override
    protected void doCancel() {
        this.parent_panel.run();
    }

    @Override
    protected void doAccept() {
        this.parent_panel.run();
    }
}
