package com.quest_enhance.common.network;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.description.PlayerPersistentDataClient;
import com.quest_enhance.common.description.PlayerPersistentDataDescription;
import com.quest_enhance.common.integration.KubeJSPersistentDataBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class QuestEnhanceNetwork {
    private static final int MAXIMUM_KEYS = 64;
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(QuestEnhance.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static boolean initialized;

    private QuestEnhanceNetwork() {
    }

    // 注册玩家持久化数据查询的双向数据包。
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CHANNEL.messageBuilder(PlayerPersistentDataRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlayerPersistentDataRequest::encode)
                .decoder(PlayerPersistentDataRequest::decode)
                .consumerMainThread(PlayerPersistentDataRequest::handle)
                .add();
        CHANNEL.messageBuilder(PlayerPersistentDataResponse.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerPersistentDataResponse::encode)
                .decoder(PlayerPersistentDataResponse::decode)
                .consumerMainThread(PlayerPersistentDataResponse::handle)
                .add();
    }

    // 仅请求当前任务描述实际引用的数据键，避免同步整份玩家 NBT。
    public static void requestPlayerPersistentData(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        CHANNEL.sendToServer(new PlayerPersistentDataRequest(keys.stream().sorted().toList()));
    }

    private static CompoundTag selectKeys(CompoundTag source, Collection<String> keys) {
        CompoundTag selected = new CompoundTag();
        if (source == null) {
            return selected;
        }
        for (String key : keys) {
            Tag value = source.get(key);
            if (value != null) {
                selected.put(key, value.copy());
            }
        }
        return selected;
    }

    private record PlayerPersistentDataRequest(List<String> keys) {
        // 编码客户端请求的有限键列表。
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.keys.size());
            this.keys.forEach(key -> buffer.writeUtf(key, PlayerPersistentDataDescription.MAXIMUM_KEY_LENGTH));
        }

        // 解码并过滤客户端提交的无效数据键。
        private static PlayerPersistentDataRequest decode(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAXIMUM_KEYS) {
                throw new IllegalArgumentException("Invalid player persistent data key count");
            }
            List<String> keys = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String key = buffer.readUtf(PlayerPersistentDataDescription.MAXIMUM_KEY_LENGTH);
                if (PlayerPersistentDataDescription.isValidKey(key) && !keys.contains(key)) {
                    keys.add(key);
                }
            }
            return new PlayerPersistentDataRequest(List.copyOf(keys));
        }

        // 服务端只读取发送者自己的 Forge 与可选 KubeJS 持久化数据。
        private static void handle(PlayerPersistentDataRequest message, Supplier<NetworkEvent.Context> context_supplier) {
            ServerPlayer player = context_supplier.get().getSender();
            if (player == null || message.keys.isEmpty()) {
                return;
            }
            CompoundTag forge_data = selectKeys(player.getPersistentData(), message.keys);
            CompoundTag kubejs_data = selectKeys(KubeJSPersistentDataBridge.get(player), message.keys);
            CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PlayerPersistentDataResponse(message.keys, forge_data, kubejs_data)
            );
        }
    }

    private record PlayerPersistentDataResponse(List<String> keys, CompoundTag forgeData, CompoundTag kubejsData) {
        // 编码服务端筛选后的 Forge 与 KubeJS 数据快照。
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.keys.size());
            this.keys.forEach(key -> buffer.writeUtf(key, PlayerPersistentDataDescription.MAXIMUM_KEY_LENGTH));
            buffer.writeNbt(this.forgeData);
            buffer.writeNbt(this.kubejsData);
        }

        // 解码服务端返回的数据快照。
        private static PlayerPersistentDataResponse decode(FriendlyByteBuf buffer) {
            PlayerPersistentDataRequest request = PlayerPersistentDataRequest.decode(buffer);
            CompoundTag forge_data = buffer.readNbt();
            CompoundTag kubejs_data = buffer.readNbt();
            return new PlayerPersistentDataResponse(
                    request.keys,
                    forge_data == null ? new CompoundTag() : forge_data,
                    kubejs_data == null ? new CompoundTag() : kubejs_data
            );
        }

        // 仅在客户端接收服务端快照并刷新当前任务详情。
        private static void handle(PlayerPersistentDataResponse message, Supplier<NetworkEvent.Context> context_supplier) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayerPersistentDataClient.receive(
                    message.keys,
                    message.forgeData,
                    message.kubejsData
            ));
        }
    }
}
