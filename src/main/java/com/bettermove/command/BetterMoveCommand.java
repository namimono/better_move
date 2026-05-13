package com.bettermove.command;

import com.bettermove.balance.DashBalanceField;
import com.bettermove.balance.DashBalanceManager;
import com.bettermove.balance.DashBalanceProfile;
import com.bettermove.tier.DashTier;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BetterMoveCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TIER =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown dash tier: " + value));
    private static final DynamicCommandExceptionType UNKNOWN_FIELD =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown balance field: " + value));

    private BetterMoveCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bettermove")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("balance")
                        .then(Commands.literal("show")
                                .executes(BetterMoveCommand::showAll)
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests(BetterMoveCommand::suggestTiers)
                                        .executes(context -> showTier(context, readTier(context)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests(BetterMoveCommand::suggestTiers)
                                        .then(Commands.argument("field", StringArgumentType.word())
                                                .suggests(BetterMoveCommand::suggestFields)
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(BetterMoveCommand::setField)))))
                        .then(Commands.literal("reset")
                                .executes(BetterMoveCommand::resetAll)
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests(BetterMoveCommand::suggestTiers)
                                        .executes(context -> resetTier(context, readTier(context)))))
                        .then(Commands.literal("reload")
                                .executes(BetterMoveCommand::reload))));
    }

    private static int showAll(CommandContext<CommandSourceStack> context) {
        DashBalanceManager manager = DashBalanceManager.get(context.getSource().getServer());
        for (DashTier tier : DashTier.values()) {
            sendProfile(context.getSource(), tier, manager.getProfile(tier));
        }
        return 1;
    }

    private static int showTier(CommandContext<CommandSourceStack> context, DashTier tier) {
        DashBalanceManager manager = DashBalanceManager.get(context.getSource().getServer());
        sendProfile(context.getSource(), tier, manager.getProfile(tier));
        return 1;
    }

    private static int setField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        DashTier tier = readTier(context);
        DashBalanceField field = readField(context);
        double value = DoubleArgumentType.getDouble(context, "value");
        DashBalanceManager manager = DashBalanceManager.get(context.getSource().getServer());
        DashBalanceProfile profile = manager.setField(tier, field, value);
        context.getSource().sendSuccess(
                () -> Component.literal("Updated " + tier.getId() + "." + field.getId() + " = " + format(value)),
                true);
        sendProfile(context.getSource(), tier, profile);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        DashBalanceManager manager = DashBalanceManager.get(context.getSource().getServer());
        manager.resetAll();
        context.getSource().sendSuccess(() -> Component.literal("Reset all dash balance tiers to defaults."), true);
        return 1;
    }

    private static int resetTier(CommandContext<CommandSourceStack> context, DashTier tier) {
        DashBalanceManager manager = DashBalanceManager.get(context.getSource().getServer());
        manager.resetTier(tier);
        context.getSource().sendSuccess(() -> Component.literal("Reset " + tier.getId() + " to defaults."), true);
        sendProfile(context.getSource(), tier, manager.getProfile(tier));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        DashBalanceManager manager = DashBalanceManager.reload(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Reloaded dash balance config."), true);
        for (DashTier tier : DashTier.values()) {
            sendProfile(context.getSource(), tier, manager.getProfile(tier));
        }
        return 1;
    }

    private static void sendProfile(CommandSourceStack source, DashTier tier, DashBalanceProfile profile) {
        source.sendSuccess(
                () -> Component.literal(
                        tier.getId()
                                + " => distance=" + format(profile.distance())
                                + ", speed=" + format(profile.speed())
                                + ", boostStrength=" + format(profile.boostStrength())
                                + ", endSpeedMultiplier=" + format(profile.endSpeedMultiplier())),
                false);
    }

    private static DashTier readTier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "tier");
        try {
            return DashTier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_TIER.create(raw);
        }
    }

    private static DashBalanceField readField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "field");
        DashBalanceField field = DashBalanceField.byId(raw);
        if (field == null) {
            throw UNKNOWN_FIELD.create(raw);
        }
        return field;
    }

    private static CompletableFuture<Suggestions> suggestTiers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        for (DashTier tier : DashTier.values()) {
            builder.suggest(tier.getId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFields(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        for (DashBalanceField field : DashBalanceField.values()) {
            builder.suggest(field.getId());
        }
        return builder.buildFuture();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
