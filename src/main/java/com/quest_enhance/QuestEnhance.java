package com.quest_enhance;

import com.quest_enhance.client.QuestEnhanceClient;
import com.quest_enhance.common.network.QuestEnhanceNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// 提供模组标识和统一日志入口
@Mod(QuestEnhance.MOD_ID)
public final class QuestEnhance {
    public static final String MOD_ID = "quest_enhance";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 只在客户端初始化配置、资源包和描述组件，服务端保留 QuestUtils 与通用 Mixin。
    public QuestEnhance(FMLJavaModLoadingContext loading_context) {
        QuestEnhanceNetwork.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestEnhanceClient.init(loading_context));
    }
}
