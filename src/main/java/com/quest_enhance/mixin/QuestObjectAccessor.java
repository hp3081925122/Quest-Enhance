package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.quest.QuestObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuestObject.class, remap = false)
public interface QuestObjectAccessor {
    // 读取 FTB 任务对象的完成提示开关。
    @Accessor("disableToast")
    boolean quest_enhance$get_disable_toast();

    // 临时修改 FTB 任务对象的完成提示开关。
    @Accessor("disableToast")
    void quest_enhance$set_disable_toast(boolean value);
}
