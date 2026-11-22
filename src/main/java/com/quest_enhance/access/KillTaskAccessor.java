package com.quest_enhance.access;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public interface KillTaskAccessor {
    ResourceLocation quest_enhance$get_entity();

    TagKey<EntityType<?>> quest_enhance$get_entity_type_tag();

    // 由独立同步数据包写回实体标签，空字符串表示未设置
    void quest_enhance$set_entity_type_tag_id(String value);
}
