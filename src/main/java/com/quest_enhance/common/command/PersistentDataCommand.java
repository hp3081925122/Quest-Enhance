package com.quest_enhance.common.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.quest_enhance.QuestEnhance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

@EventBusSubscriber(modid = QuestEnhance.MOD_ID)
public final class PersistentDataCommand {
    private PersistentDataCommand() {
    }

    // 注册用于测试 Forge 玩家持久化数据的管理员指令。
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("questenhance")
                        .then(Commands.literal("persistent")
                                .then(Commands.literal("set")
                                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .then(Commands.argument("key", StringArgumentType.word())
                                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                                .executes(PersistentDataCommand::set))))))
        );
    }

    // 将字符串写入目标玩家的 ForgeData，供任务描述组件读取。
    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");

        for (ServerPlayer player : players) {
            player.getPersistentData().putString(key, value);
        }

        context.getSource().sendSuccess(
                () -> Component.translatable("quest_enhance.command.persistent.set.success", key, players.size()),
                true
        );
        return players.size();
    }
}

