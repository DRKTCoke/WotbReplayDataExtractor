package com.wotb.core;

import com.wotb.core.model.Battle;
import com.wotb.core.model.PlayerResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Realtime rating leaderboard for an uploaded replay batch. */
public final class RatingAnalyzer {

    public static final class Row {
        public long accountId;
        public String nickname = "";
        public String clan = "";
        public int battles;
        public int wins;
        public long kills;
        public long damage;
        public long potentialDamage;
        public long potentialDamageSupplement;
        public int rating;
        public double kast;
        public double contribution;
        public double influence;
        public double damageAvg;
        public double potentialDamageAvg;
        public double potentialDamageSupplementAvg;
        public double killsAvg;
        double ratingSum;
        double effectiveContribution;
        double teamEffectiveContribution;
        double killShareDenominator;
        double enemiesDamaged;
        double enemiesDamagedShareDenominator;
        long lastTime = Long.MIN_VALUE;
        int kastBattles;

        public double winRate() {
            return battles == 0 ? 0 : 100.0 * wins / battles;
        }

        void finish() {
            if (battles == 0) {
                return;
            }
            rating = (int) Math.round(ratingSum / battles);
            kast = 100.0 * kastBattles / battles;
            contribution = teamEffectiveContribution == 0
                    ? 0 : 100.0 * effectiveContribution / teamEffectiveContribution;
            damageAvg = (double) damage / battles;
            potentialDamageAvg = (double) potentialDamage / battles;
            potentialDamageSupplementAvg = (double) potentialDamageSupplement / battles;
            killsAvg = (double) kills / battles;
            double ecShare = teamEffectiveContribution == 0
                    ? 0 : effectiveContribution / teamEffectiveContribution;
            double killShare = killShareDenominator == 0 ? 0 : kills / killShareDenominator;
            double enemyShare = enemiesDamagedShareDenominator == 0
                    ? 0 : enemiesDamaged / enemiesDamagedShareDenominator;
            double expectedTeamShare = 1.0 / 7.0;
            influence = 100.0 * (
                    0.60 * (ecShare / expectedTeamShare)
                            + 0.25 * (killShare / expectedTeamShare)
                            + 0.15 * (enemyShare / expectedTeamShare));
        }
    }

    private RatingAnalyzer() {
    }

    /**
     * Computes player rows for the currently uploaded battles.
     *
     * <p>KAST is based on the fields available in battle results: Kill, Assist damage, or Survive.
     * Trade is event-level data and is not available without parsing the full battle stream.</p>
     */
    public static List<Row> compute(List<Battle> battles, Tankopedia tp) {
        PotentialDamage.apply(battles, tp);
        Rating.compute(battles, tp);
        Map<Long, Row> rows = new LinkedHashMap<>();
        for (Battle b : battles) {
            double[] teamEc = new double[3];
            double[] teamKills = new double[3];
            double[] teamEnemiesDamaged = new double[3];
            for (PlayerResult p : b.players) {
                int team = safeTeam(p.team);
                teamEc[team] += Rating.effectiveContribution(p);
                teamKills[team] += p.kills;
                teamEnemiesDamaged[team] += p.nEnemiesDamaged;
            }

            Integer winner = b.winnerTeam;
            long start = b.startTime == null ? 0 : b.startTime;
            for (PlayerResult p : b.players) {
                Row row = rows.computeIfAbsent(p.accountId, k -> {
                    Row r = new Row();
                    r.accountId = k;
                    return r;
                });
                if (start >= row.lastTime) {
                    row.lastTime = start;
                    row.nickname = (p.nickname == null || p.nickname.isEmpty())
                            ? String.valueOf(p.accountId) : p.nickname;
                    row.clan = p.clan == null ? "" : p.clan;
                }
                int team = safeTeam(p.team);
                row.battles++;
                if (winner != null && winner != 0 && p.team == winner) {
                    row.wins++;
                }
                row.kills += p.kills;
                row.damage += p.damageDealt;
                row.potentialDamage += p.potentialDamage;
                row.potentialDamageSupplement += p.potentialDamageSupplement;
                row.ratingSum += p.rating == null ? 0 : p.rating;
                row.effectiveContribution += Rating.effectiveContribution(p);
                row.teamEffectiveContribution += teamEc[team];
                row.killShareDenominator += teamKills[team];
                row.enemiesDamaged += p.nEnemiesDamaged;
                row.enemiesDamagedShareDenominator += teamEnemiesDamaged[team];
                if (p.kills > 0 || p.damageAssisted > 0 || p.survived) {
                    row.kastBattles++;
                }
            }
        }

        List<Row> out = new ArrayList<>(rows.values());
        for (Row row : out) {
            row.finish();
        }
        out.sort((a, b) -> Integer.compare(b.rating, a.rating));
        return out;
    }

    private static int safeTeam(int team) {
        return (team == 1 || team == 2) ? team : 0;
    }
}
