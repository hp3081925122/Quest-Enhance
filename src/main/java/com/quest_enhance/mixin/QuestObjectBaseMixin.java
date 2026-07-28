package com.quest_enhance.mixin;

import com.quest_enhance.DecorativeAnchor;
import dev.ftb.mods.ftbquests.quest.ChapterImage;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(value = QuestObjectBase.class, remap = false)
public abstract class QuestObjectBaseMixin {
    // 通过新版统一复制入口为辅助点生成独立编号
    @Inject(method = "copy", at = @At("RETURN"))
    private static void quest_enhance$renew_copied_anchor_id(
            QuestObjectBase original,
            Supplier<?> factory,
            CallbackInfoReturnable<QuestObjectBase> callback_info
    ) {
        if (original instanceof ChapterImage original_image
                && callback_info.getReturnValue() instanceof ChapterImage copied_image
                && DecorativeAnchor.isAnchor(original_image)) {
            DecorativeAnchor.assignNewId(copied_image);
        }
    }
}
