package com.quest_enhance.client;

import dev.ftb.mods.ftbquests.quest.Movable;

// 保存批量复制对象及其相对粘贴锚点的位置
public record QuestEnhanceClipboardEntry(Movable object, double offset_x, double offset_y) {
}
