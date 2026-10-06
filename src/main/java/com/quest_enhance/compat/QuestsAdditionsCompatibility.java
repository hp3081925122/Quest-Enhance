package com.quest_enhance.compat;

public final class QuestsAdditionsCompatibility {
    private static final String KILL_NBT_TASK = "questsadditions.tasks.KillNbtTask";

    private QuestsAdditionsCompatibility() {
    }

    public static boolean isKillNbtTask(Object object) {
        return object != null && KILL_NBT_TASK.equals(object.getClass().getName());
    }
}
