package com.boostermod.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.boostermod.tier.BoosterTier;
import org.junit.jupiter.api.Test;

/** 满叠 ×2 后：钻石剑即使无暴击也应能秒；空手无暴击仍不秒。 */
class BoostStrikeHitDamageTest {

    @Test
    void fullStack_diamond_withoutCrit_oneshots() {
        for (BoosterTier tier : BoosterTier.values()) {
            BoostStrikeProfile profile = BoostStrikeProfile.forTier(tier);
            double damage = BoostStrikeHitDamage.estimate(
                    BoostStrikeHitDamage.DIAMOND_SWORD_BONUS,
                    profile.maxStackBonus(),
                    profile,
                    true,
                    false);
            assertTrue(
                    BoostStrikeHitDamage.oneshotsZombieOrSkeleton(damage),
                    () -> tier + " diamond no-crit=" + damage);
        }
    }

    @Test
    void fullStack_fist_withoutCrit_doesNotOneshot() {
        for (BoosterTier tier : BoosterTier.values()) {
            BoostStrikeProfile profile = BoostStrikeProfile.forTier(tier);
            double damage = BoostStrikeHitDamage.estimate(
                    0.0, profile.maxStackBonus(), profile, true, false);
            assertFalse(
                    BoostStrikeHitDamage.oneshotsZombieOrSkeleton(damage),
                    () -> tier + " fist no-crit=" + damage);
        }
    }

    @Test
    void maxStackIsDoubledFromSeven() {
        assertTrue(BoostStrikeProfile.forTier(BoosterTier.NETHERITE).maxStackBonus() == 14.0);
    }
}
