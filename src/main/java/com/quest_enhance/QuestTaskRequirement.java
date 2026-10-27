package com.quest_enhance;

import dev.ftb.mods.ftbquests.api.event.progress.ProgressEventData;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import de.marhali.json5.Json5Object;
import de.marhali.json5.Json5Primitive;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class QuestTaskRequirement {
    private static final String DATA_KEY = "quest_enhance_minimum_tasks";
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
            quest.onCompleted(new ProgressEventData<>(
                    new Date(), teamData, quest, onlineMembers, notifiedPlayers
            ));
        }
    }

    public static void writeData(Quest quest, Json5Object tag) {
        int count = getMinimumTaskCount(quest);
        if (count == 0) {
            tag.remove(DATA_KEY);
        } else {
            tag.addProperty(DATA_KEY, count);
        }
    }

    public static void readData(Quest quest, Json5Object tag) {
        int count = 0;
        if (tag.get(DATA_KEY) instanceof Json5Primitive value && value.isNumber()) {
            try {
                count = value.getAsBigDecimal().intValueExact();
            } catch (ArithmeticException | NumberFormatException ignored) {
            }
        }
        setMinimumTaskCount(quest, count);
    }

    public static void writeNetData(Quest quest, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(getMinimumTaskCount(quest));
    }

    public static void readNetData(Quest quest, RegistryFriendlyByteBuf buffer) {
        setMinimumTaskCount(quest, buffer.readVarInt());
    }
}
