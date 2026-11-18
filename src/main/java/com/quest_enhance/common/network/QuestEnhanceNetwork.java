package com.quest_enhance.common.network;

import com.quest_enhance.QuestEnhance;
import com.quest_enhance.client.QuestExtraDataClient;
import com.quest_enhance.client.description.PlayerPersistentDataClient;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import com.quest_enhance.common.description.PlayerPersistentDataDescription;
import com.quest_enhance.common.integration.KubeJSPersistentDataBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class QuestEnhanceNetwork {
    private static final int MAXIMUM_KEYS = 64;

    private QuestEnhanceNetwork() {
    }

    // 使用 NeoForge 的原生负载注册玩家持久化数据查询包。
    public static void register(RegisterPayloadHandlersEvent event) {
        // 2：扩展字段改走独立数据包，旧版本客户端与服务端不能混用
        event.registrar("2")
                .playToServer(QuestExtraDataRequest.TYPE, QuestExtraDataRequest.STREAM_CODEC, (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || ServerQuestFile.INSTANCE == null) {
                        return;
                    }
                    List<QuestExtraDataSync.Entry> entries = QuestExtraDataSync.collect(ServerQuestFile.INSTANCE);
                    for (int start = 0; start < entries.size(); start += QuestExtraDataSync.MAXIMUM_ENTRIES_PER_PACKET) {
                        int end = Math.min(entries.size(), start + QuestExtraDataSync.MAXIMUM_ENTRIES_PER_PACKET);
                        PacketDistributor.sendToPlayer(
                                player,
                                new QuestExtraDataResponse(List.copyOf(entries.subList(start, end)))
                        );
                    }
                })
                .playToClient(QuestExtraDataResponse.TYPE, QuestExtraDataResponse.STREAM_CODEC, (payload, context) ->
                        QuestExtraDataClient.receive(payload.entries)
                )
                .playToServer(PlayerPersistentDataRequest.TYPE, PlayerPersistentDataRequest.STREAM_CODEC, (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || payload.keys.isEmpty()) {
                        return;
                    }
                    context.reply(new PlayerPersistentDataResponse(
                            payload.keys,
                            selectKeys(player.getPersistentData(), payload.keys),
                            selectKeys(KubeJSPersistentDataBridge.get(player), payload.keys)
                    ));
                })
                .playToClient(PlayerPersistentDataResponse.TYPE, PlayerPersistentDataResponse.STREAM_CODEC, (payload, context) ->
                        PlayerPersistentDataClient.receive(payload.keys, payload.forgeData, payload.kubejsData)
                );
    }

    // 仅请求当前任务描述实际引用的数据键，避免同步整份玩家 NBT。
    public static void requestPlayerPersistentData(Set<String> keys) {
        if (!keys.isEmpty()) {
            PacketDistributor.sendToServer(new PlayerPersistentDataRequest(keys.stream().sorted().toList()));
        }
    }

    // 客户端收到 FTB 任务书同步后，单独向服务端请求 Quest Enhance 扩展字段
    public static void requestQuestExtraData() {
        PacketDistributor.sendToServer(new QuestExtraDataRequest());
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

    private record QuestExtraDataRequest() implements CustomPacketPayload {
        private static final Type<QuestExtraDataRequest> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(QuestEnhance.MOD_ID, "quest_extra_data_request")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, QuestExtraDataRequest> STREAM_CODEC =
                StreamCodec.unit(new QuestExtraDataRequest());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record QuestExtraDataResponse(List<QuestExtraDataSync.Entry> entries) implements CustomPacketPayload {
        private static final Type<QuestExtraDataResponse> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(QuestEnhance.MOD_ID, "quest_extra_data_response")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, QuestExtraDataResponse> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> QuestExtraDataSync.write(buffer, payload.entries),
                buffer -> new QuestExtraDataResponse(QuestExtraDataSync.read(buffer))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record PlayerPersistentDataRequest(List<String> keys) implements CustomPacketPayload {
        private static final Type<PlayerPersistentDataRequest> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(QuestEnhance.MOD_ID, "player_persistent_data_request")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerPersistentDataRequest> STREAM_CODEC = StreamCodec.of(
                PlayerPersistentDataRequest::encode,
                PlayerPersistentDataRequest::decode
        );

        // 编码客户端请求的有限键列表。
        private static void encode(RegistryFriendlyByteBuf buffer, PlayerPersistentDataRequest payload) {
            buffer.writeVarInt(payload.keys.size());
            payload.keys.forEach(key -> buffer.writeUtf(key, PlayerPersistentDataDescription.MAXIMUM_KEY_LENGTH));
        }

        // 解码并过滤客户端提交的无效数据键。
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

    private record PlayerPersistentDataResponse(List<String> keys, CompoundTag forgeData, CompoundTag kubejsData)
            implements CustomPacketPayload {
        private static final Type<PlayerPersistentDataResponse> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(QuestEnhance.MOD_ID, "player_persistent_data_response")
        );
        private static final StreamCodec<RegistryFriendlyByteBuf, PlayerPersistentDataResponse> STREAM_CODEC = StreamCodec.of(
                PlayerPersistentDataResponse::encode,
                PlayerPersistentDataResponse::decode
        );

        // 编码服务端筛选后的 Forge 与 KubeJS 数据快照。
        private static void encode(RegistryFriendlyByteBuf buffer, PlayerPersistentDataResponse payload) {
            PlayerPersistentDataRequest.encode(buffer, new PlayerPersistentDataRequest(payload.keys));
            buffer.writeNbt(payload.forgeData);
            buffer.writeNbt(payload.kubejsData);
        }

        // 解码服务端返回的数据快照。
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
