package com.quest_enhance.access;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public interface KillTaskAccessor {
    ResourceLocation quest_enhance$get_entity();

    TagKey<EntityType<?>> quest_enhance$get_entity_type_tag();
}
