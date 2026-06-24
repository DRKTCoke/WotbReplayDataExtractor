package com.wotb.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PotentialDamageTest {

    @Test
    void usesPerVictimPenetrationsAndAlpha() {
        PotentialDamage.BattlePotential r = PotentialDamage.computeBattle(3840, 400, List.of(
                new PotentialDamage.KillVictim(1, 1600, 4),
                new PotentialDamage.KillVictim(2, 1000, 3),
                new PotentialDamage.KillVictim(3, 1240, 4)
        ));

        assertEquals(4120, r.potentialDamage());
        assertEquals(280, r.supplementDamage());
        assertEquals(4120, Math.round(PotentialDamage.average(List.of(r))));
    }

    @Test
    void doesNotChangeActualDamageWhenVictimDamageMeetsThreshold() {
        PotentialDamage.BattlePotential r = PotentialDamage.computeBattle(1600, 400, List.of(
                new PotentialDamage.KillVictim(1, 1600, 4)
        ));

        assertEquals(1600, r.actualDamage());
        assertEquals(1600, r.potentialDamage());
        assertEquals(0, r.supplementDamage());
    }
}
