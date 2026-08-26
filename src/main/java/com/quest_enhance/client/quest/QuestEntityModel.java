package com.quest_enhance.client.quest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

public final class QuestEntityModel {
    public static final Identifier NONE = Identifier.fromNamespaceAndPath("quest_enhance", "none");
    private static final String ENTITY_MODEL_TAG = "quest_enhance_entity_model";

    private QuestEntityModel() {
    }

    // 从 FTB 原有图标物品的自定义 NBT 中读取模型实体注册名
    public static Optional<Identifier> getEntityModel(ItemStack icon_stack) {
        CompoundTag tag = icon_stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String entity_model_id = tag.getStringOr(ENTITY_MODEL_TAG, "");
        if (entity_model_id.isEmpty()) {
            return Optional.empty();
        }

        return Identifier.read(entity_model_id).result()
                .filter(identifier -> !identifier.getPath().isEmpty());
    }

    // 创建可由 FTB 原生任务格式保存和同步的模型占位图标物品
    public static ItemStack createModelIcon(Identifier entity_id) {
        EntityType<?> entity_type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity_id).orElse(null);
        Item item = entity_type == null
                ? Items.BARRIER
                : SpawnEggItem.byId(entity_type).map(holder -> holder.value()).orElse(Items.BARRIER);
        ItemStack icon_stack = new ItemStack(item);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                icon_stack,
                tag -> tag.putString(ENTITY_MODEL_TAG, entity_id.toString())
        );
        return icon_stack;
    }
}
