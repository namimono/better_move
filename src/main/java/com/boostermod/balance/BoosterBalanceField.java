package com.boostermod.balance;

import com.boostermod.tier.BoosterTier;

public enum BoosterBalanceField {
    DISTANCE("distance") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultDistance();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            return new BoosterBalanceProfile(value, profile.speed(), profile.boostStrength(), profile.endSpeedMultiplier());
        }
    },
    SPEED("speed") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultSpeed();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            return new BoosterBalanceProfile(profile.distance(), value, profile.boostStrength(), profile.endSpeedMultiplier());
        }
    },
    BOOST_STRENGTH("boostStrength") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultBoostStrength();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            return new BoosterBalanceProfile(profile.distance(), profile.speed(), value, profile.endSpeedMultiplier());
        }
    },
    END_SPEED_MULTIPLIER("endSpeedMultiplier") {
        @Override
        public double readDefault(BoosterTier tier) {
            return tier.getDefaultEndSpeedMultiplier();
        }

        @Override
        public BoosterBalanceProfile update(BoosterBalanceProfile profile, double value) {
            return new BoosterBalanceProfile(profile.distance(), profile.speed(), profile.boostStrength(), value);
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
