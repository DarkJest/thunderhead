package dev.tempestfx.api;

import dev.tempestfx.effect.ActiveLightningEffect;
import dev.tempestfx.effect.LightningEffectFactory;
import dev.tempestfx.config.TempestConfig;
import dev.tempestfx.lightning.LightningEnvelope;
import dev.tempestfx.lightning.LightningSegment;
import dev.tempestfx.lightning.LightningLod;
import dev.tempestfx.lightning.MidpointDisplacementStrategy;
import dev.tempestfx.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract other mods compile against. Everything here is a promise to somebody else's code,
 * which is why it is tested rather than assumed.
 */
class TempestFxApiTest {
    @AfterEach
    void detach() { TempestFxApi.Internal.uninstall(); }

    @Test
    void triggeringBeforeTheClientStartsIsANoOpRatherThanACrash() {
        TempestFxApi.Internal.uninstall();
        assertFalse(TempestFxApi.isAvailable());
        assertFalse(TempestFxApi.triggerLightning(effect(LightningStyle.DEFAULT)),
            "an optional integration must not be able to crash its host");
    }

    @Test
    void aTriggeredEffectReachesTheDispatcherWithItsStyleIntact() {
        List<LightningStrikeFxEvent> seen = new ArrayList<>();
        TempestFxApi.Internal.install(seen::add, roll -> { });
        LightningStyle style = LightningStyle.builder().thickness(2.5f).branchiness(0.2f).build();

        assertTrue(TempestFxApi.triggerLightning(effect(style)));
        assertEquals(1, seen.size());
        assertEquals(style, seen.getFirst().style());
        assertEquals(0, seen.getFirst().stroke(), "an API strike is always a first stroke");
    }

    @Test
    void listenersSeeStrikesAndCanUnsubscribe() throws Exception {
        List<LightningStrikeFxEvent> seen = new ArrayList<>();
        AutoCloseable handle = TempestFxApi.onStrike(seen::add);

        TempestFxApi.Internal.fireStrike(strike());
        assertEquals(1, seen.size());

        handle.close();
        TempestFxApi.Internal.fireStrike(strike());
        assertEquals(1, seen.size(), "a closed handle must stop delivery");
    }

    @Test
    void oneBrokenListenerDoesNotSilenceTheOthers() throws Exception {
        List<String> order = new ArrayList<>();
        AutoCloseable first = TempestFxApi.onStrike(event -> order.add("first"));
        AutoCloseable broken = TempestFxApi.onStrike(event -> { throw new IllegalStateException("boom"); });
        AutoCloseable last = TempestFxApi.onStrike(event -> order.add("last"));

        TempestFxApi.Internal.fireStrike(strike());

        assertEquals(List.of("first", "last"), order,
            "a third-party listener throwing must not cost the storm its other listeners");
        first.close();
        broken.close();
        last.close();
    }

    @Test
    void anExplicitOriginFixesTheAngleAndTheLength() {
        // "Where does it hit and from what direction" is the other half of controlling a strike.
        Vec3d ground = new Vec3d(0, 64, 0);
        Vec3d cloud = new Vec3d(40, 130, -25);
        ActiveLightningEffect effect = generate(ground, cloud, 0x0a17);

        LightningSegment first = effect.geometry().branches().getFirst().segments().getFirst();
        assertEquals(cloud.x(), first.start().x(), 1e-6);
        assertEquals(cloud.y(), first.start().y(), 1e-6);
        assertEquals(cloud.z(), first.start().z(), 1e-6, "the channel must start exactly where asked");

        // Two different origins over the same ground point must be two different bolts, or the
        // parameter is decorative.
        ActiveLightningEffect other = generate(ground, new Vec3d(-40, 130, 25), 0x0a17);
        assertNotEquals(first.start().x(),
            other.geometry().branches().getFirst().segments().getFirst().start().x());
    }

    @Test
    void withoutAnOriginTheBoltStillHangsFromTheCloud() {
        ActiveLightningEffect effect = generate(new Vec3d(0, 64, 0), null, 0x0a18);
        double top = effect.geometry().branches().getFirst().segments().getFirst().start().y();
        assertTrue(top > 64 + 100, "a derived channel reaches the cloud base, not head height: " + top);
    }

    @Test
    void styleValuesAreClampedRatherThanTrusted() {
        LightningStyle absurd = LightningStyle.builder()
            .thickness(1000f).branchiness(-5f).scale(Float.NaN).coldTint(Float.POSITIVE_INFINITY).build();

        assertEquals(4f, absurd.thickness(), 1e-6);
        assertEquals(0f, absurd.branchiness(), 1e-6);
        assertEquals(1f, absurd.scale(), 1e-6, "a non-finite value falls back to neutral");
        assertEquals(1f, absurd.coldTint(), 1e-6);
    }

    @Test
    void intensityIsClampedOnTheWayIn() {
        assertEquals(LightningEffect.MAX_INTENSITY,
            LightningEffect.builder().position(Vec3d.ZERO).intensity(500f).build().intensity(), 1e-6,
            "an integration must not be able to produce a flash nobody consented to");
        assertTrue(LightningEffect.builder().position(Vec3d.ZERO).intensity(-1f).build().intensity() > 0);
        assertEquals(1f, LightningEffect.builder().position(Vec3d.ZERO).intensity(Float.NaN).build().intensity(), 1e-6);
    }

    @Test
    void anAbsentStyleMeansThePlayersOwnSettings() {
        LightningEffect plain = LightningEffect.builder().position(Vec3d.ZERO).build();
        assertNull(plain.style(), "no style means the player's configuration, not a neutral style");
        assertFalse(plain.styled());
        assertTrue(LightningEffect.builder().position(Vec3d.ZERO).style(LightningStyle.DEFAULT).build().styled());
    }

    @Test
    void thePlayersSettingsGovernTheModsOwnStrikes() {
        TempestConfig quiet = new TempestConfig();
        quiet.lightning.scale = 0.5f;
        quiet.validate();
        TempestConfig stock = new TempestConfig().validate();

        assertEquals(0.5, channelHeight(quiet, null) / channelHeight(stock, null), 1e-6,
            "an unstyled strike must follow the player's own scale");
    }

    @Test
    void aStyledStrikeIgnoresThePlayersLookSettings() {
        // The integration asked for this bolt, so the integration decides how it looks. A player who
        // retuned their own storms does not restyle somebody else's set piece.
        TempestConfig quiet = new TempestConfig();
        quiet.lightning.scale = 0.5f;
        quiet.validate();
        TempestConfig loud = new TempestConfig();
        loud.lightning.scale = 2f;
        loud.validate();

        LightningStyle style = LightningStyle.builder().scale(1.5f).build();
        assertEquals(channelHeight(quiet, style), channelHeight(loud, style), 1e-6,
            "two players with opposite settings must see the same styled strike");
        assertEquals(1.5, channelHeight(quiet, style) / channelHeight(quiet, null) * 0.5, 1e-6,
            "a styled strike is measured against the mod's stock look, not the player's");
    }

    @Test
    void accessibilityIsNotStylable() {
        // Reduced flashing is read from the configuration wherever brightness is computed, and no
        // style field reaches it. If a style could dim or brighten, this is where it would show.
        LightningEnvelope envelope = new LightningEnvelope(0x99);
        boolean normalRestrikes = false;
        boolean reducedRestrikes = false;
        float previousNormal = Float.MAX_VALUE;
        float previousReduced = Float.MAX_VALUE;
        for (float t = 0; t < LightningEnvelope.DURATION_TICKS; t += 0.05f) {
            float normal = envelope.brightness(t, true, false);
            float reduced = envelope.brightness(t, true, true);
            if (normal > previousNormal + 1e-4f) normalRestrikes = true;
            if (reduced > previousReduced + 1e-4f) reducedRestrikes = true;
            previousNormal = normal;
            previousReduced = reduced;
        }
        assertTrue(normalRestrikes, "a normal flash re-strikes; if it did not, this test proves nothing");
        assertFalse(reducedRestrikes, "reduced flashing must decay once and never flash again");
        for (String field : List.of("brightness", "flicker", "reducedFlashing", "intensity")) {
            assertFalse(java.util.Arrays.stream(LightningStyle.class.getRecordComponents())
                    .anyMatch(component -> component.getName().equalsIgnoreCase(field)),
                "a style must not be able to reach " + field);
        }
    }

    /**
     * Where the main channel leaves the cloud, which is what {@code scale} drives.
     */
    private static double channelHeight(TempestConfig config, LightningStyle style) {
        LightningEffectFactory factory = new LightningEffectFactory(new MidpointDisplacementStrategy());
        ActiveLightningEffect effect = factory.create(
            new LightningStrikeFxEvent(Vec3d.ZERO, 0x51713, 1f, LightningEnvironment.land(0x777777, false),
                StrikeTarget.none(), 0, StrikeOptions.builder().style(style).build()),
            LightningLod.FULL, config);
        return effect.geometry().branches().getFirst().segments().getFirst().start().y();
    }

    private static ActiveLightningEffect generate(Vec3d ground, Vec3d origin, long seed) {
        return new LightningEffectFactory(new MidpointDisplacementStrategy()).create(
            new LightningStrikeFxEvent(ground, seed, 1f, LightningEnvironment.land(0x777777, false),
                StrikeTarget.none(), 0, StrikeOptions.builder().origin(origin).build()),
            LightningLod.FULL, new TempestConfig().validate());
    }

    private static LightningEffect effect(LightningStyle style) {
        return LightningEffect.builder().position(new Vec3d(1, 2, 3)).seed(7).style(style).build();
    }

    @Test
    void everyOverrideSurvivesTheTripThroughTheApi() {
        // One strike carrying all four overrides at once: the bag must arrive whole.
        List<LightningStrikeFxEvent> seen = new ArrayList<>();
        TempestFxApi.Internal.install(seen::add, roll -> { });
        LightningStyle style = LightningStyle.builder().color(0xFF8800).thickness(2f).build();
        Vec3d origin = new Vec3d(10, 90, -5);
        ThunderOptions thunder = new ThunderOptions(ThunderVoice.DISTANT_THUNDER, 0.5f, 12);

        TempestFxApi.triggerLightning(LightningEffect.builder()
            .position(Vec3d.ZERO).seed(3).style(style).origin(origin).thunder(thunder)
            .particles(ParticleFamily.SPARKS).build());

        StrikeOptions options = seen.getFirst().options();
        assertEquals(style, options.style());
        assertEquals(origin, options.origin());
        assertEquals(thunder, options.thunder());
        assertTrue(options.allows(ParticleFamily.SPARKS));
        assertFalse(options.allows(ParticleFamily.SMOKE), "an unlisted family must be filtered out");
        assertEquals(0xFF8800, options.style().coreColor());
    }

    @Test
    void aRollIsRefusedGracefullyWhenTheClientIsDown() {
        TempestFxApi.Internal.uninstall();
        assertFalse(TempestFxApi.triggerThunderRoll(ThunderRoll.at(Vec3d.ZERO, 1)));
    }

    @Test
    void absurdOptionValuesAreClampedOnTheWayIn() {
        ThunderOptions loud = new ThunderOptions(ThunderVoice.AUTO, 99f, 100_000);
        assertEquals(2f, loud.volume(), 1e-6);
        assertEquals(ThunderOptions.MAX_DELAY_TICKS, loud.delayTicks());
        assertTrue(ThunderOptions.DEFAULT.delayFromDistance(), "the honest delay is the default");

        ThunderRoll roll = new ThunderRoll(Vec3d.ZERO, 1, 99_999, 99_999);
        assertEquals(320, roll.durationTicks());
        assertEquals(100, roll.flashesPerSecond());

        assertEquals(LightningStyle.AUTOMATIC,
            LightningStyle.builder().coreColor(0x1FFFFFF).build().coreColor(),
            "a value outside 24-bit RGB is 'not specified', not an error");
    }

    private static LightningStrikeFxEvent strike() {
        return new LightningStrikeFxEvent(Vec3d.ZERO, 1, 1f, LightningEnvironment.land(0x777777, false));
    }
}
