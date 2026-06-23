package com.rfizzle.tribulation.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Client-side threat-telegraphing particle. A single class drives all three
 * cues; the {@link Style} bakes in the per-cue motion (rise/sink/drift),
 * lifetime, size, and base alpha so each reads distinctly. Alpha eases out over
 * the cue's lifetime so it fades rather than popping. Purely cosmetic — no
 * physics, no collision, no light.
 */
public class ThreatParticle extends TextureSheetParticle {
    private final float baseAlpha;

    protected ThreatParticle(ClientLevel level, double x, double y, double z,
                             double xd, double yd, double zd, SpriteSet sprites, Style style) {
        super(level, x, y, z);
        this.friction = 0.94f;
        this.hasPhysics = false;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = style.gravity;
        this.lifetime = style.minLife + this.random.nextInt(style.lifeJitter + 1);
        this.quadSize = style.size * (0.85f + this.random.nextFloat() * 0.3f);
        this.baseAlpha = style.alpha;
        this.alpha = style.alpha;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // Ease alpha out over the lifetime (quadratic) so the cue fades softly.
        float f = this.lifetime <= 0 ? 1.0f : (float) this.age / (float) this.lifetime;
        this.alpha = this.baseAlpha * (1.0f - f * f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Per-cue motion and appearance presets. */
    public enum Style {
        /** Cursed mote: drifts slowly upward, lingers, faint. */
        TIER(-0.006f, 24, 18, 0.10f, 0.85f),
        /** Sooty dust: heavier, sinks, short-lived, larger. */
        BIG(0.012f, 16, 12, 0.16f, 0.90f),
        /** Afterimage streak: no gravity, quick, sharp. */
        SPEED(0.0f, 8, 8, 0.12f, 0.80f);

        final float gravity;
        final int minLife;
        final int lifeJitter;
        final float size;
        final float alpha;

        Style(float gravity, int minLife, int lifeJitter, float size, float alpha) {
            this.gravity = gravity;
            this.minLife = minLife;
            this.lifeJitter = lifeJitter;
            this.size = size;
            this.alpha = alpha;
        }
    }

    /** Factory bound to one {@link Style}; registered once per particle type. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new ThreatParticle(level, x, y, z, xd, yd, zd, sprites, style);
        }
    }
}
