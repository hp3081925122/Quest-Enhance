package com.quest_enhance.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Optional;

public final class QuestEntityModel {
    public static final ResourceLocation NONE = ResourceLocation.fromNamespaceAndPath("quest_enhance", "none");
    private static final String ENTITY_MODEL_TAG = "quest_enhance_entity_model";

    private QuestEntityModel() {
    }

    // 从 FTB 原有图标物品的自定义 NBT 中读取模型实体注册名
    public static Optional<ResourceLocation> getEntityModel(ItemStack icon_stack) {
        CompoundTag tag = icon_stack.getTag();
        if (tag == null || !tag.contains(ENTITY_MODEL_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }

        return ResourceLocation.read(tag.getString(ENTITY_MODEL_TAG)).result();
    }

    // 创建可由 FTB 原生任务格式保存和同步的模型占位图标物品
    public static ItemStack createModelIcon(ResourceLocation entity_id) {
        EntityType<?> entity_type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity_id).orElse(null);
        SpawnEggItem spawn_egg = entity_type == null ? null : SpawnEggItem.byId(entity_type);
        Item item = spawn_egg == null ? Items.BARRIER : spawn_egg;
        ItemStack icon_stack = new ItemStack(item);
        icon_stack.getOrCreateTag().putString(ENTITY_MODEL_TAG, entity_id.toString());
        return icon_stack;
    }
}
