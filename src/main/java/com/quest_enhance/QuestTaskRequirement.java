package com.quest_enhance;

import dev.ftb.mods.ftbquests.events.QuestProgressEventData;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class QuestTaskRequirement {
    private static final String NBT_KEY = "quest_enhance_minimum_tasks";
    private static final Map<Quest, Integer> MINIMUM_TASKS = Collections.synchronizedMap(new WeakHashMap<>());

    private QuestTaskRequirement() {
    }

    public static int getMinimumTaskCount(Quest quest) {
        return MINIMUM_TASKS.getOrDefault(quest, 0);
    }

    public static void setMinimumTaskCount(Quest quest, int count) {
        if (count <= 0) {
            MINIMUM_TASKS.remove(quest);
        } else {
            MINIMUM_TASKS.put(quest, count);
        }
    }

    public static int getRequiredTaskCount(Quest quest) {
        return Math.min(getMinimumTaskCount(quest), quest.getTasks().size());
    }

    public static int getCompletedTaskCount(Quest quest, TeamData teamData) {
        int completed = 0;
        for (Task task : quest.getTasks()) {
            if (teamData.isCompleted(task)) {
                completed++;
            }
        }
        return completed;
    }

    public static boolean isCompleted(Quest quest, TeamData teamData) {
        int required = getRequiredTaskCount(quest);
        return required > 0 && teamData.canStartTasks(quest)
                && getCompletedTaskCount(quest, teamData) >= required;
    }

    public static int getRelativeProgress(Quest quest, TeamData teamData) {
        if (teamData.getCompletionCount(quest) > 0) {
            return 100;
        }
        int required = getRequiredTaskCount(quest);
        if (required == 0 || !teamData.areDependenciesComplete(quest)) {
            return 0;
        }
        return (int) Math.min(100L, getCompletedTaskCount(quest, teamData) * 100L / required);
    }

    public static boolean hasEnoughTaskProgress(Quest quest, TeamData teamData) {
        int required = getRequiredTaskCount(quest);
        if (required == 0) {
            return false;
        }
        int completed = 0;
        for (Task task : quest.getTasks()) {
            if (teamData.getProgress(task) >= task.getMaxProgress() && ++completed >= required) {
                return true;
            }
        }
        return false;
    }

    public static void checkCompletionAfterEdit(Quest quest) {
        if (getRequiredTaskCount(quest) == 0) {
            return;
        }
        BaseQuestFile file = quest.getQuestFile();
        if (!file.isServerSide()) {
            return;
        }
        for (TeamData teamData : file.getAllTeamData()) {
            if (teamData.isLocked() || teamData.isCompleted(quest) || !isCompleted(quest, teamData)) {
                continue;
            }
            Collection<ServerPlayer> onlineMembers = teamData.getOnlineMembers();
            Collection<ServerPlayer> notifiedPlayers = quest.getChapter().isAlwaysInvisible()
                    || !QuestObjectBase.shouldSendNotifications() ? List.of() : onlineMembers;
            quest.onCompleted(new QuestProgressEventData<>(
                    new Date(), teamData, quest, onlineMembers, notifiedPlayers
            ));
        }
    }

    public static void writeData(Quest quest, CompoundTag tag) {
        int count = getMinimumTaskCount(quest);
        if (count == 0) {
            tag.remove(NBT_KEY);
        } else {
            tag.putInt(NBT_KEY, count);
        }
    }

    public static void readData(Quest quest, CompoundTag tag) {
        setMinimumTaskCount(quest, tag.getInt(NBT_KEY));
    }

    public static void writeNetData(Quest quest, FriendlyByteBuf buffer) {
        buffer.writeVarInt(getMinimumTaskCount(quest));
    }

    public static void readNetData(Quest quest, FriendlyByteBuf buffer) {
        setMinimumTaskCount(quest, buffer.readVarInt());
    }
}
