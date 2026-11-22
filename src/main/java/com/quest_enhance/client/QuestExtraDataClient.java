package com.quest_enhance.client;

import com.quest_enhance.common.network.QuestExtraDataSync;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;

import java.util.List;

// 客户端接收 Quest Enhance 扩展字段并刷新任务书界面
public final class QuestExtraDataClient {
    private QuestExtraDataClient() {
    }

    public static void receive(List<QuestExtraDataSync.Entry> entries) {
        ClientQuestFile file = ClientQuestFile.INSTANCE;
        if (file == null) {
            return;
        }
        QuestExtraDataSync.apply(file, entries);
        file.refreshGui();
    }
}
