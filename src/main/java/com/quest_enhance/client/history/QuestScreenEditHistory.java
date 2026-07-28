package com.quest_enhance.client.history;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

// 在普通客户端包保存画布撤销历史，避免 Forge 将其识别为 Mixin 类
public final class QuestScreenEditHistory {
    public final Deque<Snapshot> undo = new ArrayDeque<>();
    public final Deque<Snapshot> redo = new ArrayDeque<>();
    public Snapshot current;
    public boolean ignore_next_snapshot;

    // 保存章节图片和现有任务节点序列化后的稳定编辑状态
    public record Snapshot(CompoundTag chapter, Map<Long, CompoundTag> quests) {
    }
}
