package com.quest_enhance.mixin;

import com.quest_enhance.QuestTaskRequirement;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestObjectBase.class, remap = false)
public abstract class QuestTaskRequirementEditMixin {
    @Inject(method = "editedFromGUIOnServer", at = @At("TAIL"))
    private void quest_enhance$check_minimum_tasks_after_edit(CallbackInfo callbackInfo) {
        if ((Object) this instanceof Quest quest) {
            QuestTaskRequirement.checkCompletionAfterEdit(quest);
        }
    }
}
