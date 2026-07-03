package com.rfizzle.tribulation.champion;

import com.rfizzle.tribulation.config.TribulationConfig.Champions;

/**
 * The champion affix pool. Each affix has a stable string id (persisted in the
 * champion attachment and shown by {@code /tribulation inspect}), a translation
 * key for the name tag, and a per-affix config toggle read from
 * {@link Champions.Affixes}.
 */
public enum ChampionAffix {
    VAMPIRIC("vampiric"),
    EXPLOSIVE("explosive"),
    KNOCKBACK_AURA("knockback_aura"),
    THORNS("thorns"),
    REGENERATING("regenerating");

    private final String id;

    ChampionAffix(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "affix.tribulation." + id;
    }

    /** Resolve an affix from its persisted id; returns {@code null} for unknown ids. */
    public static ChampionAffix byId(String id) {
        for (ChampionAffix affix : values()) {
            if (affix.id.equals(id)) return affix;
        }
        return null;
    }

    public boolean isEnabled(Champions.Affixes cfg) {
        if (cfg == null) return false;
        return switch (this) {
            case VAMPIRIC -> cfg.vampiric;
            case EXPLOSIVE -> cfg.explosive;
            case KNOCKBACK_AURA -> cfg.knockbackAura;
            case THORNS -> cfg.thorns;
            case REGENERATING -> cfg.regenerating;
        };
    }
}
