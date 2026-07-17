package com.quest_enhance.mixin;

import com.quest_enhance.client.QuestDescriptionTable;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextField.class, remap = false)
public abstract class TextFieldMixin {
    @Shadow
    private Component rawText;

    // 表格组件在初次设置和界面重排时都按实际表格高度覆盖普通文字行高
    @Inject(method = "resize", at = @At("RETURN"))
    private void quest_enhance$resize_table(
            Theme theme,
            CallbackInfoReturnable<TextField> callback_info
    ) {
        QuestDescriptionTable.find(this.rawText).ifPresent(table -> {
            TextField field = (TextField) (Object) this;
            QuestDescriptionTable.TableLayout layout = QuestDescriptionTable.layout(
                    theme,
                    field.maxWidth,
                    table
            );
            field.setWidth(layout.width());
            field.setHeight(layout.height());
        });
    }
}
