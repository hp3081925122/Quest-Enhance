package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = QuestObjectBase.class, remap = false)
public interface QuestObjectBaseAccessor {
    // 读取任务对象由 FTB 原生格式保存的图标物品
    @Accessor("rawIcon")
    ItemStack quest_enhance$get_raw_icon();

    // 调用 FTB 原生编辑校验，保持批量编辑与单任务编辑的一致性。
    @Invoker("validateEditedConfig")
    boolean quest_enhance$validate_edited_config();
}
