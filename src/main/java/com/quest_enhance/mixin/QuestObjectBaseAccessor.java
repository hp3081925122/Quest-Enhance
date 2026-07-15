package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuestObjectBase.class, remap = false)
public interface QuestObjectBaseAccessor {
    // 读取任务对象由 FTB 原生格式保存的图标物品
    @Accessor("rawIcon")
    ItemStack quest_enhance$get_raw_icon();
}
