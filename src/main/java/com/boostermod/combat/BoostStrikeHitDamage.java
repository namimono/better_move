package com.boostermod.combat;

/**
 * 破击窗口内一次主目标近战的伤害估算（不含护甲/抗性/附魔）。
 * 对齐现有实现：属性攻击力 × 蓄力比例 ×（暴击 1.5）+ 当次 {@code bonusDamage}。
 */
public final class BoostStrikeHitDamage {
    /** 原版玩家空手基础攻击力。 */
    public static final double FIST_ATTACK = 1.0;
    /** 铁剑相对空手的攻击力加成（总属性 6）。 */
    public static final double IRON_SWORD_BONUS = 5.0;
    /** 钻石剑相对空手的攻击力加成（总属性 7）。 */
    public static final double DIAMOND_SWORD_BONUS = 6.0;
    /** 下界合金剑相对空手的攻击力加成（总属性 8）。 */
    public static final double NETHERITE_SWORD_BONUS = 7.0;
    /** 原版僵尸 / 骷髅基础生命。 */
    public static final double ZOMBIE_OR_SKELETON_HP = 20.0;
    public static final double CRIT_MULTIPLIER = 1.5;

    private BoostStrikeHitDamage() {}

    /**
     * @param weaponAttackBonus 武器相对空手的攻击力加成（钻石剑 6 → 总属性 7）
     * @param stackAmount 破击叠层加到 {@code ATTACK_DAMAGE} 的量
     * @param profile 品质分档
     * @param fullStrength 是否满蓄力
     * @param critical 是否暴击（破击窗口内仅满蓄力时强制为 true）
     */
    public static double estimate(
            double weaponAttackBonus,
            double stackAmount,
            BoostStrikeProfile profile,
            boolean fullStrength,
            boolean critical) {
        double attribute = FIST_ATTACK + Math.max(0.0, weaponAttackBonus) + Math.max(0.0, stackAmount);
        double strengthScale = fullStrength ? 1.0 : 0.0;
        double main = attribute * strengthScale;
        if (critical) {
            main *= CRIT_MULTIPLIER;
        }
        return main + profile.bonusDamage();
    }

    /** 破击窗口 + 满蓄力 + 强制暴击。 */
    public static double estimateFullChargeCrit(
            double weaponAttackBonus, double stackAmount, BoostStrikeProfile profile) {
        return estimate(weaponAttackBonus, stackAmount, profile, true, true);
    }

    public static boolean oneshotsZombieOrSkeleton(double damage) {
        return damage >= ZOMBIE_OR_SKELETON_HP;
    }
}
