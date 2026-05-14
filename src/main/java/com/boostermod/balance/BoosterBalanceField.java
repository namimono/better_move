package com.boostermod.balance;

import com.boostermod.tier.BoosterTier;

public enum BoosterBalanceField {
    IMPULSE("impulse") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultImpulse();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            return new BoosterBalanceProfile(value, profile.thrustPerTick(), profile.thrustTicks());
        }
    },
    THRUST_PER_TICK("thrustPerTick") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultThrustPerTick();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            return new BoosterBalanceProfile(profile.impulse(), value, profile.thrustTicks());
        }
    },
    THRUST_TICKS("thrustTicks") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultThrustTicks();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            int ticks = (int) Math.max(0, Math.round(value));
            return new BoosterBalanceProfile(profile.impulse(), profile.thrustPerTick(), ticks);
        }
    };

    private final String id;

    BoosterBalanceField(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public abstract double readDefault(BoosterTier tier);

    public abstract BoosterBalanceProfile update(BoosterBalanceProfile profile, double value);

    public static BoosterBalanceField byId(String id) {
        for (BoosterBalanceField field : values()) {
            if (field.id.equals(id)) {
                return field;
            }
        }
        return null;
    }
}
