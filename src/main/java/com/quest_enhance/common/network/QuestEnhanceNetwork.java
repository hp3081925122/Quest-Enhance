package com.quest_enhance.common.network;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.common.description.PlayerPersistentDataDescription;
import com.quest_enhance.common.integration.KubeJSPersistentDataBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class QuestEnhanceNetwork {
    private static final int MAXIMUM_KEYS = 64;
    private static Method client_receive;
    private static boolean client_receive_checked;
    private static Method client_send_to_server;
    private static boolean client_send_checked;

    private QuestEnhanceNetwork() {
    }

    // 使用 NeoForge 原生负载注册玩家持久化数据查询包
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(
                        PlayerPersistentDataRequest.TYPE,
                        PlayerPersistentDataRequest.STREAM_CODEC,
                        (payload, context) -> {
                            if (!(context.player() instanceof ServerPlayer player) || payload.keys.isEmpty()) {
                                return;
                            }
                            context.reply(new PlayerPersistentDataResponse(
                                    payload.keys,
                                    selectKeys(player.getPersistentData(), payload.keys),
                                    selectKeys(KubeJSPersistentDataBridge.get(player), payload.keys)
                            ));
                        }
                )
                .playToClient(
                        PlayerPersistentDataResponse.TYPE,
                        PlayerPersistentDataResponse.STREAM_CODEC,
                        (payload, context) -> receiveClientData(
                                payload.keys,
                                payload.forgeData,
                                payload.kubejsData
                        )
                );
    }

    // 仅请求当前任务描述实际引用的数据键，避免同步整份玩家 NBT
    public static void requestPlayerPersistentData(Set<String> keys) {
        if (!keys.isEmpty()) {
            sendClientRequest(new PlayerPersistentDataRequest(keys.stream().sorted().toList()));
        }
    }

    // 通过反射调用客户端发包入口，避免服务端加载 NeoForge 客户端类
    private static void sendClientRequest(PlayerPersistentDataRequest request) {
        if (!client_send_checked) {
            client_send_checked = true;
            try {
                client_send_to_server = Class.forName(
                                "net.neoforged.neoforge.client.network.ClientPacketDistributor"
                        )
                        .getMethod(
                                "sendToServer",
                                CustomPacketPayload.class,
                                CustomPacketPayload[].class
                        );
            } catch (ReflectiveOperationException | LinkageError ignored) {
                client_send_to_server = null;
            }
        }
        if (client_send_to_server == null) {
            return;
        }
        try {
            client_send_to_server.invoke(
                    null,
                    request,
                    new CustomPacketPayload[0]
            );
        } catch (ReflectiveOperationException | LinkageError ignored) {
            QuestEnhance.LOGGER.debug("Failed to send persistent data request to the server");
        }
    }

    // 通过反射调用客户端接收器，避免服务端加载客户端类
    private static void receiveClientData(
            List<String> keys,
            CompoundTag forge_data,
            CompoundTag kubejs_data
    ) {
        if (!client_receive_checked) {
            client_receive_checked = true;
            try {
                client_receive = Class.forName(
                                "com.quest_enhance.client.description.PlayerPersistentDataClient"
                        )
                        .getMethod("receive", List.class, CompoundTag.class, CompoundTag.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                client_receive = null;
            }
        }
        if (client_receive == null) {
            return;
        }
        try {
            client_receive.invoke(null, keys, forge_data, kubejs_data);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            QuestEnhance.LOGGER.debug("Failed to deliver persistent data response to the client");
        }
    }

    // 只保留请求键对应的数据，避免发送玩家的其他持久化内容
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

    private record PlayerPersistentDataRequest(List<String> keys) implements CustomPacketPayload {
        private static final Type<PlayerPersistentDataRequest> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(QuestEnhance.MOD_ID, "player_persistent_data_request")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerPersistentDataRequest> STREAM_CODEC = StreamCodec.of(
                PlayerPersistentDataRequest::encode,
                PlayerPersistentDataRequest::decode
        );

        // 编码客户端请求的有限键列表
        private static void encode(RegistryFriendlyByteBuf buffer, PlayerPersistentDataRequest payload) {
            buffer.writeVarInt(payload.keys.size());
            payload.keys.forEach(key -> buffer.writeUtf(key, PlayerPersistentDataDescription.MAXIMUM_KEY_LENGTH));
        }

        // 解码并过滤客户端提交的无效数据键
        private static PlayerPersistentDataRequest decode(RegistryFriendlyByteBuf buffer) {
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

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record PlayerPersistentDataResponse(
            List<String> keys,
            CompoundTag forgeData,
            CompoundTag kubejsData
    ) implements CustomPacketPayload {
        private static final Type<PlayerPersistentDataResponse> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(QuestEnhance.MOD_ID, "player_persistent_data_response")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerPersistentDataResponse> STREAM_CODEC = StreamCodec.of(
                PlayerPersistentDataResponse::encode,
                PlayerPersistentDataResponse::decode
        );

        // 编码服务端筛选后的 Forge 与 KubeJS 数据快照
        private static void encode(RegistryFriendlyByteBuf buffer, PlayerPersistentDataResponse payload) {
            PlayerPersistentDataRequest.encode(buffer, new PlayerPersistentDataRequest(payload.keys));
            buffer.writeNbt(payload.forgeData);
            buffer.writeNbt(payload.kubejsData);
        }

        // 解码服务端返回的数据快照
        private static PlayerPersistentDataResponse decode(RegistryFriendlyByteBuf buffer) {
            PlayerPersistentDataRequest request = PlayerPersistentDataRequest.decode(buffer);
            CompoundTag forge_data = buffer.readNbt();
            CompoundTag kubejs_data = buffer.readNbt();
            return new PlayerPersistentDataResponse(
                    request.keys,
                    forge_data == null ? new CompoundTag() : forge_data,
                    kubejs_data == null ? new CompoundTag() : kubejs_data
            );
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
