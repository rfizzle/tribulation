// Tier: 2 (fabric-loader-junit — RandomSource/Component without registry bootstrap)
package com.rfizzle.tribulation.champion;

import com.rfizzle.tribulation.config.TribulationConfig.Champions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChampionManagerTest {

    private static Champions champions() {
        return new Champions();
    }

    // ---- shouldRoll ----

    @Test
    void shouldRoll_belowThreshold_isFalse() {
        Champions cfg = champions();
        cfg.levelThreshold = 50;
        assertFalse(ChampionManager.shouldRoll(49, cfg));
    }

    @Test
    void shouldRoll_atThreshold_isTrue() {
        Champions cfg = champions();
        cfg.levelThreshold = 50;
        assertTrue(ChampionManager.shouldRoll(50, cfg));
    }

    @Test
    void shouldRoll_disabled_isFalse() {
        Champions cfg = champions();
        cfg.enabled = false;
        assertFalse(ChampionManager.shouldRoll(250, cfg));
    }

    @Test
    void shouldRoll_zeroChance_isFalse() {
        Champions cfg = champions();
        cfg.championChance = 0.0;
        assertFalse(ChampionManager.shouldRoll(250, cfg));
    }

    @Test
    void shouldRoll_nullConfig_isFalse() {
        assertFalse(ChampionManager.shouldRoll(250, null));
    }

    // ---- enabledAffixes ----

    @Test
    void enabledAffixes_defaultConfig_containsAll() {
        assertEquals(List.of(ChampionAffix.values()),
                ChampionManager.enabledAffixes(champions().affixes));
    }

    @Test
    void enabledAffixes_respectsToggles() {
        Champions.Affixes affixes = champions().affixes;
        affixes.explosive = false;
        affixes.regenerating = false;
        List<ChampionAffix> pool = ChampionManager.enabledAffixes(affixes);
        assertFalse(pool.contains(ChampionAffix.EXPLOSIVE));
        assertFalse(pool.contains(ChampionAffix.REGENERATING));
        assertTrue(pool.contains(ChampionAffix.VAMPIRIC));
        assertTrue(pool.contains(ChampionAffix.KNOCKBACK_AURA));
        assertTrue(pool.contains(ChampionAffix.THORNS));
    }

    @Test
    void enabledAffixes_nullConfig_isEmpty() {
        assertTrue(ChampionManager.enabledAffixes(null).isEmpty());
    }

    // ---- selectAffixes ----

    @Test
    void selectAffixes_alwaysDistinctAndWithinBounds() {
        RandomSource random = RandomSource.create(12345);
        List<ChampionAffix> pool = List.of(ChampionAffix.values());
        for (int i = 0; i < 200; i++) {
            List<ChampionAffix> picked = ChampionManager.selectAffixes(pool, 2, random);
            assertTrue(picked.size() >= 1 && picked.size() <= 2,
                    "must pick 1..2 affixes, got " + picked.size());
            assertEquals(picked.size(), new HashSet<>(picked).size(), "affixes must be distinct");
        }
    }

    @Test
    void selectAffixes_maxAffixesClampedToPoolSize() {
        RandomSource random = RandomSource.create(1);
        List<ChampionAffix> pool = List.of(ChampionAffix.VAMPIRIC);
        for (int i = 0; i < 20; i++) {
            assertEquals(List.of(ChampionAffix.VAMPIRIC),
                    ChampionManager.selectAffixes(pool, 5, random));
        }
    }

    @Test
    void selectAffixes_eventuallyPicksEveryPoolMember() {
        RandomSource random = RandomSource.create(99);
        List<ChampionAffix> pool = List.of(ChampionAffix.values());
        Set<ChampionAffix> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.addAll(ChampionManager.selectAffixes(pool, 2, random));
        }
        assertEquals(Set.of(ChampionAffix.values()), seen);
    }

    @Test
    void selectAffixes_emptyPool_isEmpty() {
        assertTrue(ChampionManager.selectAffixes(List.of(), 2, RandomSource.create(1)).isEmpty());
    }

    // ---- championName ----

    @Test
    void championName_oneAffix_usesOneKey() {
        Component name = ChampionManager.championName(
                List.of(ChampionAffix.VAMPIRIC), Component.literal("Zombie"));
        TranslatableContents contents = (TranslatableContents) name.getContents();
        assertEquals("champion.tribulation.name.one", contents.getKey());
        assertEquals(2, contents.getArgs().length);
    }

    @Test
    void championName_twoAffixes_usesTwoKey() {
        Component name = ChampionManager.championName(
                List.of(ChampionAffix.VAMPIRIC, ChampionAffix.THORNS), Component.literal("Zombie"));
        TranslatableContents contents = (TranslatableContents) name.getContents();
        assertEquals("champion.tribulation.name.two", contents.getKey());
        assertEquals(3, contents.getArgs().length);
    }

    // ---- applyChampionXp ----

    @Test
    void applyChampionXp_nonChampion_isUnchanged() {
        assertEquals(10, ChampionManager.applyChampionXp(10, false, champions()));
    }

    @Test
    void applyChampionXp_champion_multiplies() {
        Champions cfg = champions();
        cfg.xpMultiplier = 3.0;
        assertEquals(30, ChampionManager.applyChampionXp(10, true, cfg));
    }

    @Test
    void applyChampionXp_disabledOrUnityMultiplier_isUnchanged() {
        Champions disabled = champions();
        disabled.enabled = false;
        assertEquals(10, ChampionManager.applyChampionXp(10, true, disabled));

        Champions unity = champions();
        unity.xpMultiplier = 1.0;
        assertEquals(10, ChampionManager.applyChampionXp(10, true, unity));
    }

    @Test
    void applyChampionXp_overflow_clampsToMaxInt() {
        Champions cfg = champions();
        cfg.xpMultiplier = 3.0;
        assertEquals(Integer.MAX_VALUE,
                ChampionManager.applyChampionXp(Integer.MAX_VALUE - 1, true, cfg));
    }

    // ---- ChampionAffix ----

    @Test
    void affixById_roundTrips() {
        for (ChampionAffix affix : ChampionAffix.values()) {
            assertEquals(affix, ChampionAffix.byId(affix.id()));
        }
        assertNull(ChampionAffix.byId("not_an_affix"));
    }
}
