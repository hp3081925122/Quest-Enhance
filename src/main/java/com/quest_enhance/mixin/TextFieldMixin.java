package com.quest_enhance.mixin;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.description.QuestDescriptionTable;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TextField.class, remap = false)
public abstract class TextFieldMixin {
    @Shadow
    private Component rawText;
    private boolean quest_enhance$table_logged;

    // 1.20.1 的描述字段继承 TextField.draw，因此只允许 FTB Quests 专用描述字段绘制表格。
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$draw_table(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        if (!this.getClass().getName().equals(
                "dev.ftb.mods.ftbquests.client.gui.quests.ViewQuestPanel$QuestDescriptionField"
        )) {
            return;
        }
        QuestDescriptionTable.find(this.rawText).ifPresent(table -> {
            if (!this.quest_enhance$table_logged) {
                QuestEnhance.LOGGER.debug("Rendering table in the FTB Quests description field");
                this.quest_enhance$table_logged = true;
            }
            QuestDescriptionTable.draw(graphics, theme, x, y, width, table);
            callback_info.cancel();
        });
    }

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
