package com.quest_enhance.mixin;

import dev.ftb.mods.ftbquests.events.QuestProgressEventData;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.Task;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Task.class, remap = false)
public abstract class TaskCompletionMixin {
    // 任务完成并触发所属任务节点时，同步继承任务的“不显示提示”设置。
    @Redirect(
            method = "onCompleted",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbquests/quest/Quest;onCompleted(Ldev/ftb/mods/ftbquests/events/QuestProgressEventData;)V"
            )
    )
    private void quest_enhance$inherit_disabled_toast(
            Quest quest,
            QuestProgressEventData<?> data
    ) {
        Task task = (Task) (Object) this;
        if (!((QuestObjectAccessor) (Object) task).quest_enhance$get_disable_toast()) {
            quest.onCompleted(data);
            return;
        }

        QuestObjectAccessor quest_accessor = (QuestObjectAccessor) (Object) quest;
        boolean original_value = quest_accessor.quest_enhance$get_disable_toast();
        quest_accessor.quest_enhance$set_disable_toast(true);
        try {
            quest.onCompleted(data);
        } finally {
            quest_accessor.quest_enhance$set_disable_toast(original_value);
        }
    }
}
