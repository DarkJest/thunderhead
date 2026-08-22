package dev.tempestfx.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.effect.ActiveLuminousEvent;
import dev.tempestfx.effect.AshImprint;
import dev.tempestfx.effect.CloudLightSource;
import dev.tempestfx.effect.EntityDischarge;
import dev.tempestfx.effect.RodCorona;
import dev.tempestfx.effect.ShockwaveEffect;
import dev.tempestfx.effect.TransientLightSystem.TransientPointLight;
import dev.tempestfx.math.Vec3d;
import dev.tempestfx.particle.FxParticle;
import java.util.List;

/**
 * Orders the world-space effect passes.
 *
 * <p>Work is grouped by pass rather than by effect, so the cost is a fixed number of draw calls
 * regardless of how many bolts, particles or decals are alive. Each batch is acquired, filled and
 * flushed before the next pass is requested.
 *
 * <p>Nothing in here, and nothing below it, knows where the geometry lands: {@link FxBatchTarget}
 * owns the programs, the buffers, the GL state and the framebuffer. That is what lets one renderer
 * serve the mod's own isolated pass and the fallback that draws straight into the scene.
 */
public final class WorldFxRenderer {
    private final LightningRenderer lightningRenderer = new LightningRenderer();
    private final ShockwaveRenderer shockwaveRenderer = new ShockwaveRenderer();
    private final ParticleRenderer particleRenderer = new ParticleRenderer();
    private final TransientLightRenderer lightRenderer = new TransientLightRenderer();
    private final EntityDischargeRenderer dischargeRenderer = new EntityDischargeRenderer();
    private final AshImprintRenderer imprintRenderer = new AshImprintRenderer();
    private final BallLightningRenderer sphereRenderer = new BallLightningRenderer();
    private final CloudIlluminationRenderer cloudRenderer = new CloudIlluminationRenderer();
    private final LuminousEventRenderer luminousRenderer = new LuminousEventRenderer();
    private final StreamerRenderer streamerRenderer = new StreamerRenderer();
    private final RodCoronaRenderer rodRenderer = new RodCoronaRenderer();

    /** Snapshot of everything the world pass needs; keeps the signature readable. */
    public record Scene(List<ActiveLightningEffect> lightning,
                        List<ShockwaveEffect> shockwaves,
                        List<FxParticle> particles,
                        List<TransientPointLight> lights,
                        List<EntityDischarge> discharges,
                        List<AshImprint> imprints,
                        List<ActiveLightningEffect> distantBolts,
                        List<BallLightningDraw> spheres,
                        List<ActiveLightningEffect> skyDischarges,
                        List<CloudLightSource> cloudLights,
                        List<ActiveLuminousEvent> luminousEvents,
                        List<RodCorona> rodCoronas) {
        public boolean isEmpty() {
            return lightning.isEmpty() && shockwaves.isEmpty() && particles.isEmpty()
                && lights.isEmpty() && discharges.isEmpty() && imprints.isEmpty()
                && distantBolts.isEmpty() && spheres.isEmpty()
                && skyDischarges.isEmpty() && cloudLights.isEmpty() && luminousEvents.isEmpty()
                && rodCoronas.isEmpty();
        }
    }

    public void render(Scene scene, PoseStack stack, FxBatchTarget target,
                       Vec3d camera, float partialTick, TempestConfig config, float emissiveBoost,
                       ShaderPackProfile profile) {
        float emissive = emissiveBoost * profile.emissiveScale();
        PoseStack.Pose pose = stack.last();

        // Ground decals first: they sit under everything else that lands on the same surface.
        if (!scene.imprints().isEmpty()) {
            pass(target, FxPass.DECAL,
                consumer -> imprintRenderer.renderDecal(scene.imprints(), pose, consumer, partialTick));
            pass(target, FxPass.DECAL_EMBER,
                consumer -> imprintRenderer.renderEmbers(scene.imprints(), pose, consumer, partialTick));
        }
        if (!scene.shockwaves().isEmpty() && config.impact.surfaceRipple) {
            pass(target, FxPass.RIPPLE, consumer -> {
                for (ShockwaveEffect effect : scene.shockwaves()) {
                    shockwaveRenderer.renderSurfaceRipple(effect, pose, consumer, partialTick, config);
                }
            });
        }
        // Lit cloud goes under everything electrical: a channel inside a glowing region has to read
        // as being inside it, and the region is the dimmer, wider thing.
        if (!scene.cloudLights().isEmpty() && profile.drawsWideGlow()) {
            pass(target, FxPass.CLOUD_LIGHT,
                consumer -> cloudRenderer.render(scene.cloudLights(), pose, consumer, camera, partialTick));
        }
        if (!scene.shockwaves().isEmpty() || !scene.distantBolts().isEmpty()
            || !scene.luminousEvents().isEmpty()) {
            if (profile.drawsWideGlow()) pass(target, FxPass.ATMOSPHERE, consumer -> {
                for (ShockwaveEffect effect : scene.shockwaves()) {
                    shockwaveRenderer.renderHaze(effect, pose, consumer, camera, partialTick);
                }
                // One quad per distant channel: the cloud it came out of lighting up behind it.
                for (ActiveLightningEffect effect : scene.distantBolts()) {
                    lightningRenderer.renderCloudGlow(effect, pose, consumer, camera, partialTick);
                }
                // A sprite is mostly this. The filaments give it a silhouette; the diffuse light is
                // what actually carries across four hundred blocks of sky.
                luminousRenderer.renderGlow(scene.luminousEvents(), pose, consumer, camera, partialTick,
                    config.general.reducedFlashing);
            });
        }
        if (!scene.shockwaves().isEmpty()) {
            if (profile.drawsWideGlow()) pass(target, FxPass.FLASH, consumer -> {
                for (ShockwaveEffect effect : scene.shockwaves()) {
                    shockwaveRenderer.renderBurst(effect, pose, consumer, camera, partialTick);
                }
            });
        }
        if (!scene.lights().isEmpty() || !scene.shockwaves().isEmpty() || !scene.particles().isEmpty()
            || !scene.spheres().isEmpty() || StreamerRenderer.any(scene.lightning())
            || RodCoronaRenderer.any(scene.rodCoronas())) {
            if (profile.drawsWideGlow()) pass(target, FxPass.GLOW, consumer -> {
                lightRenderer.render(scene.lights(), pose, consumer, partialTick);
                streamerRenderer.renderAttachmentFlash(scene.lightning(), pose, consumer, camera, partialTick);
                rodRenderer.renderGlow(scene.rodCoronas(), pose, consumer, camera, partialTick);
                for (ShockwaveEffect effect : scene.shockwaves()) {
                    shockwaveRenderer.renderFlash(effect, pose, consumer, camera, partialTick);
                }
                particleRenderer.renderGlowing(scene.particles(), pose, consumer, camera, partialTick);
                for (BallLightningDraw sphere : scene.spheres()) {
                    sphereRenderer.renderGroundPool(sphere, pose, consumer, sphere.x(), sphere.y(), sphere.z());
                    sphereRenderer.renderCore(sphere, pose, consumer, sphere.x(), sphere.y(), sphere.z(),
                        camera.x(), camera.y(), camera.z());
                }
            });
        }
        if (!scene.spheres().isEmpty() && profile.drawsWideGlow()) {
            pass(target, FxPass.PLASMA, consumer -> {
                for (BallLightningDraw sphere : scene.spheres()) {
                    sphereRenderer.renderShell(sphere, pose, consumer, sphere.x(), sphere.y(), sphere.z());
                }
            });
        }

        // Everything electrical shares one additive batch: channels, rings, sparks, arcs, spray.
        if (!scene.lightning().isEmpty() || !scene.shockwaves().isEmpty() || !scene.discharges().isEmpty()
            || !scene.particles().isEmpty() || !scene.distantBolts().isEmpty() || !scene.spheres().isEmpty()
            || !scene.skyDischarges().isEmpty() || !scene.luminousEvents().isEmpty()
            || !scene.rodCoronas().isEmpty()) {
            pass(target, FxPass.BOLT, consumer -> {
                for (ActiveLightningEffect effect : scene.lightning()) {
                    lightningRenderer.render(effect, stack, consumer, camera, partialTick, config, emissive, profile);
                }
                for (ActiveLightningEffect effect : scene.skyDischarges()) {
                    lightningRenderer.render(effect, stack, consumer, camera, partialTick, config, emissive, profile);
                }
                luminousRenderer.renderFilaments(scene.luminousEvents(), pose, consumer, camera, partialTick,
                    config.general.reducedFlashing, profile);
                streamerRenderer.renderStreamers(scene.lightning(), pose, consumer, camera, partialTick, profile);
                rodRenderer.renderArcs(scene.rodCoronas(), pose, consumer, camera, partialTick);
                for (ShockwaveEffect effect : scene.shockwaves()) {
                    shockwaveRenderer.renderRing(effect, pose, consumer, camera, partialTick, config);
                }
                for (ActiveLightningEffect effect : scene.distantBolts()) {
                    lightningRenderer.render(effect, stack, consumer, camera, partialTick, config, emissive, profile);
                }
                dischargeRenderer.render(scene.discharges(), pose, consumer, camera, partialTick);
                particleRenderer.renderStreaks(scene.particles(), pose, consumer, camera, partialTick);
                for (BallLightningDraw sphere : scene.spheres()) {
                    sphereRenderer.renderArcs(sphere, pose, consumer, sphere.x(), sphere.y(), sphere.z(),
                        camera.x(), camera.y(), camera.z(), profile);
                }
            });
        }

        if (!scene.particles().isEmpty()) {
            pass(target, FxPass.DEBRIS,
                consumer -> particleRenderer.renderDebris(scene.particles(), pose, consumer, camera, partialTick));
            pass(target, FxPass.SOFT,
                consumer -> particleRenderer.renderSoft(scene.particles(), pose, consumer, camera, partialTick));
            pass(target, FxPass.SMOKE,
                consumer -> particleRenderer.renderSmoke(scene.particles(), pose, consumer, camera, partialTick));
        }
    }

    private static void pass(FxBatchTarget target, FxPass pass, PassBody body) {
        VertexConsumer consumer = target.begin(pass);
        try {
            body.emit(consumer);
        } finally {
            target.end(pass);
        }
    }

    @FunctionalInterface
    private interface PassBody {
        void emit(VertexConsumer consumer);
    }
}
