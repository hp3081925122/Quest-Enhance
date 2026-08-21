package com.quest_enhance.mixin;

import com.quest_enhance.client.config.QuestEnhanceClientConfig;
import com.quest_enhance.client.quest.KillTaskEntityPreview;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.quest.task.AdvancementTask;
import dev.ftb.mods.ftbquests.quest.task.KillTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TaskButton.class, remap = false)
public abstract class TaskButtonMixin {
    @Shadow
    Task task;

    @Unique
    private KillTaskEntityPreview quest_enhance$entity_preview;

    // 左键点击有效的进度任务时打开对应的原版进度页
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$open_advancement_task(
            MouseButton mouse_button,
            CallbackInfo callback_info
    ) {
        if (!mouse_button.isLeft()
                || !QuestEnhanceClientConfig.OPEN_ADVANCEMENT_TASKS.get()
                || !(this.task instanceof AdvancementTask advancement_task)) {
            return;
        }

        // 只在客户端已经同步对应原版进度时拦截点击，找不到数据则保留 FTB 原逻辑
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        ResourceLocation advancement_id = ((AdvancementTaskAccessor) advancement_task)
                .quest_enhance$get_advancement();
        if (advancement_id == null) {
            return;
        }
        ClientAdvancements advancements = minecraft.getConnection().getAdvancements();
        Advancement advancement = advancements.getAdvancements().get(advancement_id);
        if (advancement == null) {
            return;
        }

        // 先选择对应根节点，再按原版 L 键路径打开进度界面
        advancements.setSelectedTab(advancement.getRoot(), true);
        minecraft.setScreen(new AdvancementsScreen(advancements));
        callback_info.cancel();
    }

    // 在开关启用时用缓存的生物模型替代击杀任务刷怪蛋图标
    @Inject(method = "drawIcon", at = @At("HEAD"), cancellable = true)
    private void quest_enhance$draw_kill_task_entity(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callback_info
    ) {
        if (!QuestEnhanceClientConfig.RENDER_KILL_TASK_ENTITY_MODELS.get()
                || !(this.task instanceof KillTask kill_task)) {
            return;
        }

        // 延迟创建预览缓存并按击杀任务当前实体注册名绘制
        if (this.quest_enhance$entity_preview == null) {
            this.quest_enhance$entity_preview = new KillTaskEntityPreview();
        }
        ResourceLocation entity_id = ((KillTaskAccessor) kill_task).quest_enhance$get_entity();
        if (this.quest_enhance$entity_preview.render(entity_id, graphics, x, y, width, height)) {
            callback_info.cancel();
        }
    }
}
