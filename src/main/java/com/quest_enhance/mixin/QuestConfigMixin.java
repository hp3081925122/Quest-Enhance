package com.quest_enhance.mixin;

import com.quest_enhance.client.QuestEntityModel;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Quest.class, remap = false)
public abstract class QuestConfigMixin {
    // 在原生图标配置下方增加可持久化的实体模型选择项
    @Inject(method = "fillConfigGroup", at = @At("TAIL"))
    private void quest_enhance$add_entity_model_config(ConfigGroup config, CallbackInfo callback_info) {
        Quest quest = (Quest) (Object) this;
        ItemStack raw_icon = ((QuestObjectBaseAccessor) (Object) quest).quest_enhance$get_raw_icon();
        ResourceLocation initial_model = QuestEntityModel.getEntityModel(raw_icon).orElse(QuestEntityModel.NONE);

        // 构建“无”和全部实体注册名组成的原生枚举选择器
        List<ResourceLocation> entity_ids = new ArrayList<>();
        entity_ids.add(QuestEntityModel.NONE);
        entity_ids.addAll(BuiltInRegistries.ENTITY_TYPE.keySet());
        NameMap<ResourceLocation> entity_models = NameMap.of(QuestEntityModel.NONE, entity_ids)
                .name(entity_id -> entity_id.equals(QuestEntityModel.NONE)
                        ? Component.translatable("quest_enhance.none")
                        : Component.translatable("entity." + entity_id.getNamespace() + "." + entity_id.getPath()))
                .icon(entity_id -> {
                    if (entity_id.equals(QuestEntityModel.NONE)) {
                        return Color4I.empty();
                    }

                    EntityType<?> entity_type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity_id).orElse(null);
                    SpawnEggItem spawn_egg = entity_type == null ? null : SpawnEggItem.byId(entity_type);
                    return ItemIcon.getItemIcon((Item) (spawn_egg == null ? Items.BARRIER : spawn_egg));
                })
                .create();

        // 只在模型选项发生变化时覆盖图标，保证普通图标编辑仍按 FTB 原逻辑生效
        config.addEnum("entity_model", initial_model, selected_model -> {
            if (selected_model.equals(initial_model)) {
                return;
            }

            if (selected_model.equals(QuestEntityModel.NONE)) {
                ItemStack current_icon = ((QuestObjectBaseAccessor) (Object) quest).quest_enhance$get_raw_icon();
                if (QuestEntityModel.getEntityModel(current_icon).isPresent()) {
                    quest.setRawIcon(ItemStack.EMPTY);
                }
            } else {
                quest.setRawIcon(QuestEntityModel.createModelIcon(selected_model));
            }
            quest.clearCachedData();
        }, entity_models, QuestEntityModel.NONE)
                .setNameKey("quest_enhance.quest_model")
                .setOrder(-125);

        // 给模型项腾出图标下方的位置，并保持标签和后续字段的原有相对顺序
        for (ConfigValue<?> value : config.getValues()) {
            if ("tags".equals(value.id)) {
                value.setOrder(-124);
                break;
            }
        }
    }
}
