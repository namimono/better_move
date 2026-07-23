package com.boostermod.command;

import com.boostermod.BoosterMod;
import com.boostermod.balance.BoosterBalanceField;
import com.boostermod.balance.BoosterBalanceManager;
import com.boostermod.balance.BoosterBalanceProfile;
import com.boostermod.feedback.BoosterShakeSettings;
import com.boostermod.hud.BoosterHudSettings;
import com.boostermod.tier.BoosterTier;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BoosterModCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TIER =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown booster tier: " + value));
    private static final DynamicCommandExceptionType UNKNOWN_FIELD =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown balance field: " + value));

    private BoosterModCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("boostermod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("hud")
                        .then(Commands.literal("status")
                                .executes(BoosterModCommand::hudStatus))
                        .then(Commands.literal("on")
                                .executes(context -> setHudEnabled(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> setHudEnabled(context, false))))
                .then(Commands.literal("shake")
                        .then(Commands.literal("status")
                                .executes(BoosterModCommand::shakeStatus))
                        .then(Commands.literal("on")
                                .executes(context -> setShakeEnabled(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> setShakeEnabled(context, false))))
                .then(Commands.literal("balance")
                        .then(Commands.literal("show")
                                .executes(BoosterModCommand::showAll)
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests(BoosterModCommand::suggestTiers)
                                        .executes(context -> showTier(context, readTier(context)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests(BoosterModCommand::suggestTiers)
                                        .then(Commands.argument("field", StringArgumentType.word())
                                                .suggests(BoosterModCommand::suggestFields)
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(BoosterModCommand::setField)))))
                        .then(Commands.literal("reset")
                                .executes(BoosterModCommand::resetAll)
                                .then(Commands.argument("tier", StringArgumentType.word())
                                        .suggests(BoosterModCommand::suggestTiers)
                                        .executes(context -> resetTier(context, readTier(context)))))
                        .then(Commands.literal("reload")
                                .executes(BoosterModCommand::reload))));
    }

    private static int showAll(CommandContext<CommandSourceStack> context) {
        BoosterBalanceManager manager = BoosterBalanceManager.get(context.getSource().getServer());
        for (BoosterTier tier : BoosterTier.values()) {
            sendProfile(context.getSource(), tier, manager.getProfile(tier));
        }
        return 1;
    }

    private static int showTier(CommandContext<CommandSourceStack> context, BoosterTier tier) {
        BoosterBalanceManager manager = BoosterBalanceManager.get(context.getSource().getServer());
        sendProfile(context.getSource(), tier, manager.getProfile(tier));
        return 1;
    }

    private static int setField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BoosterTier tier = readTier(context);
        BoosterBalanceField field = readField(context);
        double value = DoubleArgumentType.getDouble(context, "value");
        BoosterBalanceManager manager = BoosterBalanceManager.get(context.getSource().getServer());
        BoosterBalanceProfile profile = manager.setField(tier, field, value);
        context.getSource().sendSuccess(
                () -> Component.literal("Updated " + tier.getId() + "." + field.getId() + " = " + format(value)),
                true);
        sendProfile(context.getSource(), tier, profile);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        BoosterBalanceManager manager = BoosterBalanceManager.get(context.getSource().getServer());
        manager.resetAll();
        context.getSource().sendSuccess(() -> Component.literal("Reset all booster balance tiers to defaults."), true);
        return 1;
    }

    private static int resetTier(CommandContext<CommandSourceStack> context, BoosterTier tier) {
        BoosterBalanceManager manager = BoosterBalanceManager.get(context.getSource().getServer());
        manager.resetTier(tier);
        context.getSource().sendSuccess(() -> Component.literal("Reset " + tier.getId() + " to defaults."), true);
        sendProfile(context.getSource(), tier, manager.getProfile(tier));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        BoosterBalanceManager manager = BoosterBalanceManager.reload(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Reloaded booster balance config."), true);
        for (BoosterTier tier : BoosterTier.values()) {
            sendProfile(context.getSource(), tier, manager.getProfile(tier));
        }
        return 1;
    }

    private static int hudStatus(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoosterHudSettings.get(context.getSource().getServer()).isEnabled();
        context.getSource().sendSuccess(
                () -> Component.literal("Booster HUD is currently " + (enabled ? "enabled" : "disabled") + "."),
                false);
        return 1;
    }

    private static int setHudEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        BoosterHudSettings settings = BoosterHudSettings.get(context.getSource().getServer());
        boolean changed = settings.setEnabled(enabled);
        BoosterMod.syncHudState(context.getSource().getServer());
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Booster HUD " + (enabled ? "enabled" : "disabled") + (changed ? "." : " (unchanged).")),
                true);
        return 1;
    }

    private static int shakeStatus(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoosterShakeSettings.get(context.getSource().getServer()).isEnabled();
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Booster screen shake is currently " + (enabled ? "enabled" : "disabled") + "."),
                false);
        return 1;
    }

    private static int setShakeEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        BoosterShakeSettings settings = BoosterShakeSettings.get(context.getSource().getServer());
        boolean changed = settings.setEnabled(enabled);
        BoosterMod.syncShakeState(context.getSource().getServer());
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Booster screen shake "
                                + (enabled ? "enabled" : "disabled")
                                + (changed ? "." : " (unchanged).")),
                true);
        return 1;
    }

    private static void sendProfile(CommandSourceStack source, BoosterTier tier, BoosterBalanceProfile profile) {
        source.sendSuccess(
                () -> Component.literal(
                        tier.getId()
                                + " => impulse=" + format(profile.impulse())
                                + ", thrustPerTick=" + format(profile.thrustPerTick())
                                + ", thrustTicks=" + profile.thrustTicks()),
                false);
    }

    private static BoosterTier readTier(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "tier");
        try {
            return BoosterTier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_TIER.create(raw);
        }
    }

    private static BoosterBalanceField readField(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "field");
        BoosterBalanceField field = BoosterBalanceField.byId(raw);
        if (field == null) {
            throw UNKNOWN_FIELD.create(raw);
        }
        return field;
    }

    private static CompletableFuture<Suggestions> suggestTiers(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        for (BoosterTier tier : BoosterTier.values()) {
            builder.suggest(tier.getId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestFields(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        for (BoosterBalanceField field : BoosterBalanceField.values()) {
            builder.suggest(field.getId());
        }
        return builder.buildFuture();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
