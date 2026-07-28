package com.boostermod.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmedVillagerTargetModeTest {
    @Test
    void byIdAcceptsCanonicalAndEnumNames() {
        assertEquals(ArmedVillagerTargetMode.PLAYERS, ArmedVillagerTargetMode.byId("players").orElseThrow());
        assertEquals(ArmedVillagerTargetMode.MONSTERS, ArmedVillagerTargetMode.byId("monsters").orElseThrow());
        assertEquals(ArmedVillagerTargetMode.PLAYERS, ArmedVillagerTargetMode.byId("PLAYERS").orElseThrow());
        assertEquals(ArmedVillagerTargetMode.MONSTERS, ArmedVillagerTargetMode.byId("Monsters").orElseThrow());
    }

    @Test
    void byIdRejectsUnknown() {
        assertTrue(ArmedVillagerTargetMode.byId("").isEmpty());
        assertTrue(ArmedVillagerTargetMode.byId("mobs").isEmpty());
        assertTrue(ArmedVillagerTargetMode.byId(null).isEmpty());
    }
}
