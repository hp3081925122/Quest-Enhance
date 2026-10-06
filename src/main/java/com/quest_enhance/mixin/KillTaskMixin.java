package com.quest_enhance.mixin;

import com.quest_enhance.access.KillTaskAccessor;
import com.quest_enhance.compat.QuestsAdditionsCompatibility;
import com.quest_enhance.config.EntityTagConfig;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = KillTask.class, remap = false)
public abstract class KillTaskMixin implements KillTaskAccessor {
    @Shadow
    private ResourceLocation entity;

    @Unique
    private TagKey<EntityType<?>> quest_enhance$entity_type_tag;

    @Unique
    public ResourceLocation quest_enhance$get_entity() {
        return this.entity;
    }

    @Unique
    public TagKey<EntityType<?>> quest_enhance$get_entity_type_tag() {
        return this.quest_enhance$entity_type_tag;
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void quest_enhance$write_entity_type_tag(
            CompoundTag tag,
            CallbackInfo callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        if (this.quest_enhance$entity_type_tag != null) {
            tag.putString(
                    "entityTypeTag",
                    this.quest_enhance$entity_type_tag.location().toString()
            );
        }
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void quest_enhance$read_entity_type_tag(
            CompoundTag tag,
            CallbackInfo callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        this.quest_enhance$entity_type_tag = quest_enhance$parse_entity_type_tag(
                tag.getString("entityTypeTag")
        );
    }

    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void quest_enhance$write_entity_type_tag_net(
            FriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        buffer.writeUtf(
                this.quest_enhance$entity_type_tag == null
                        ? ""
                        : this.quest_enhance$entity_type_tag.location().toString(),
                32767
        );
    }

    @Inject(method = "readNetData", at = @At("TAIL"))
    private void quest_enhance$read_entity_type_tag_net(
            FriendlyByteBuf buffer,
            CallbackInfo callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        this.quest_enhance$entity_type_tag = quest_enhance$parse_entity_type_tag(
                buffer.readUtf(32767)
        );
    }

    @Inject(method = "fillConfigGroup", at = @At("TAIL"))
    private void quest_enhance$add_entity_type_tag_config(
            ConfigGroup group,
            CallbackInfo callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        group.add(
                "entity_type_tag",
                new EntityTagConfig(quest_enhance$entity_type_tag_map()),
                this.quest_enhance$get_entity_type_tag_id(),
                value -> this.quest_enhance$entity_type_tag = quest_enhance$parse_entity_type_tag(value),
                ""
        );
        if (this.quest_enhance$entity_type_tag != null) {
            group.getValues().removeIf(config_value -> "entity".equals(config_value.id));
        }
    }

    @Inject(method = "getAltTitle", at = @At("RETURN"), cancellable = true)
    private void quest_enhance$show_entity_type_tag_title(
            CallbackInfoReturnable<MutableComponent> callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        if (this.quest_enhance$entity_type_tag == null) {
            return;
        }

        KillTask task = (KillTask) (Object) this;
        callback_info.setReturnValue(Component.translatable(
                "ftbquests.task.ftbquests.kill.title",
                task.formatMaxProgress(),
                Component.literal(this.quest_enhance$get_entity_type_tag_string())
        ));
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$match_entity_type_tag(
            TeamData team_data,
            LivingEntity entity,
            CallbackInfo callback_info
    ) {
        if (QuestsAdditionsCompatibility.isKillNbtTask(this)) {
            return;
        }
        if (this.quest_enhance$entity_type_tag == null) {
            return;
        }

        KillTask task = (KillTask) (Object) this;
        if (!team_data.isCompleted((QuestObject) task)
                && entity.getType().is(this.quest_enhance$entity_type_tag)) {
            team_data.addProgress(task, 1L);
        }
        callback_info.cancel();
    }

    @Unique
    private String quest_enhance$get_entity_type_tag_string() {
        return this.quest_enhance$entity_type_tag == null
                ? ""
                : "#" + this.quest_enhance$get_entity_type_tag_id();
    }

    @Unique
    private String quest_enhance$get_entity_type_tag_id() {
        return this.quest_enhance$entity_type_tag == null
                ? ""
                : this.quest_enhance$entity_type_tag.location().toString();
    }

    @Unique
    private static NameMap<String> quest_enhance$entity_type_tag_map() {
        List<String> tag_ids = new ArrayList<>();
        tag_ids.add("");
        BuiltInRegistries.ENTITY_TYPE.getTags()
                .map(pair -> pair.getFirst().location().toString())
                .sorted()
                .forEach(tag_ids::add);
        return NameMap.of("", tag_ids).create();
    }

    @Unique
    private static TagKey<EntityType<?>> quest_enhance$parse_entity_type_tag(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String tag_id = value.startsWith("#") ? value.substring(1) : value;
        ResourceLocation location = ResourceLocation.tryParse(tag_id);
        return location == null
                ? null
                : TagKey.create(Registries.ENTITY_TYPE, location);
    }
}
