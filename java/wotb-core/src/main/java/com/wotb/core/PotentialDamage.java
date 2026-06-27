package com.wotb.core;

import com.wotb.core.model.Battle;
import com.wotb.core.model.PlayerResult;

import java.util.List;

/** Computes potential damage from per-victim kill damage details. */
public final class PotentialDamage {

    /** Damage details against one victim killed by the attacker. */
    public record KillVictim(long victimAccountId, int damage, int penetrations) {
    }

    /** One battle's potential damage result for a player. */
    public record BattlePotential(int actualDamage, int potentialDamage, int supplementDamage) {
    }

    private static final double MIN_ALPHA_RATIO = 0.9;

    private PotentialDamage() {
    }

    /**
     * Potential damage preserves actual damage and adds only the missing part against killed victims.
     *
     * <p>For each killed victim, if {@code damage / penetrations < 0.9 * alphaDamage}, that victim's
     * damage is raised to {@code penetrations * 0.9 * alphaDamage}. The total supplement is then added
     * to the player's actual battle damage.</p>
     */
    public static BattlePotential computeBattle(int actualDamage, int alphaDamage, List<KillVictim> victims) {
        if (actualDamage < 0) {
            throw new IllegalArgumentException("actualDamage must be >= 0");
        }
        if (alphaDamage <= 0 || victims == null || victims.isEmpty()) {
            return new BattlePotential(actualDamage, actualDamage, 0);
        }
        int supplement = 0;
        for (KillVictim victim : victims) {
            supplement += supplementForVictim(alphaDamage, victim);
        }
        return new BattlePotential(actualDamage, actualDamage + supplement, supplement);
    }

    public static double average(List<BattlePotential> battles) {
        if (battles == null || battles.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (BattlePotential battle : battles) {
            total += battle.potentialDamage();
        }
        return (double) total / battles.size();
    }

    /** Applies the current potential-damage rule to every player in a replay batch. */
    public static void apply(List<Battle> battles, Tankopedia tp) {
        if (battles == null) {
            return;
        }
        for (Battle battle : battles) {
            for (PlayerResult player : battle.players) {
                Integer alpha = tp.info(player.tankId).alphaDamage;
                BattlePotential result = computeBattle(player.damageDealt,
                        alpha == null ? 0 : alpha,
                        player.killVictims);
                player.potentialDamage = result.potentialDamage();
                player.potentialDamageSupplement = result.supplementDamage();
                player.potentialDamageDetailed = !player.killVictims.isEmpty();
            }
        }
    }
    private static int supplementForVictim(int alphaDamage, KillVictim victim) {
        if (victim == null || victim.damage() < 0 || victim.penetrations() <= 0) {
            return 0;
        }
        double minimum = victim.penetrations() * alphaDamage * MIN_ALPHA_RATIO;
        if (victim.damage() >= minimum) {
            return 0;
        }
        return (int) Math.ceil(minimum - victim.damage());
    }
}
