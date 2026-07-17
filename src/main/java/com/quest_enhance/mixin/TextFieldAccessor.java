package com.quest_enhance.mixin;

import dev.ftb.mods.ftblibrary.ui.TextField;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TextField.class, remap = false)
public interface TextFieldAccessor {
    // 读取文字字段用于排版的原始组件
    @Accessor("rawText")
    Component quest_enhance$get_raw_text();
}
