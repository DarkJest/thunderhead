package dev.tempestfx.lightning;

import dev.tempestfx.api.DischargeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DischargeProfilesTest {
    @Test
    void everyArchetypeHasAProfileOfItsOwn() {
        for (DischargeType type : DischargeType.values()) {
            DischargeProfile profile = DischargeProfiles.of(type);
            assertNotNull(profile, type + " has no profile");
            assertEquals(type, profile.type());
        }
    }

    @Test
    void aPositiveFlashIsADifferentEventRatherThanABrighterOne() {
        DischargeProfile negative = DischargeProfiles.of(DischargeType.NEGATIVE_CLOUD_TO_GROUND);
        DischargeProfile positive = DischargeProfiles.of(DischargeType.POSITIVE_CLOUD_TO_GROUND);

        assertTrue(positive.widthScale() > negative.widthScale() * 1.5, "positive channel must be visibly wider");
        assertTrue(positive.branchScale() < negative.branchScale() * 0.5, "positive channel must be barer");
        assertTrue(positive.warmth() > 0.4f, "positive channel must not be the same colour");
        assertTrue(positive.envelope().durationTicks() > negative.envelope().durationTicks(),
            "positive flash must hold rather than stutter");
        assertTrue(positive.envelope().maxRestrikes() < negative.envelope().maxRestrikes(),
            "positive flash is one dominant stroke");
        assertTrue(positive.thunderScale() > negative.thunderScale());
    }

    @Test
    void aerialArchetypesDoNotHangACanopyOffACloudBase() {
        for (DischargeType type : DischargeType.values()) {
            if (!type.aerial()) continue;
            DischargeProfile profile = DischargeProfiles.of(type);
            assertEquals(0.0, profile.canopyScale(), 1e-9, type + " must not grow a ground bolt's canopy");
            assertTrue(Math.abs(profile.forkBiasY()) < 0.1, type + " forks must spread flat, not hang down");
        }
    }

    @Test
    void anIntracloudEventIsMostlyCloudAndBarelyChannel() {
        DischargeProfile intracloud = DischargeProfiles.of(DischargeType.INTRACLOUD);
        DischargeProfile negative = DischargeProfiles.of(DischargeType.NEGATIVE_CLOUD_TO_GROUND);
        assertTrue(intracloud.channelOpacity() < 0.3f, "the channel must stay buried");
        assertTrue(intracloud.cloudGlow() > negative.cloudGlow() * 2, "the cloud must do the work instead");
    }

    @Test
    void aMegaflashOutreachesEverythingElse() {
        DischargeProfile mega = DischargeProfiles.of(DischargeType.MEGAFLASH);
        assertTrue(mega.envelope().propagationTicks() > 10f, "a megaflash must be seen to travel");
        assertTrue(mega.envelope().durationTicks() > 20f);
        assertTrue(mega.cloudGlow() > DischargeProfiles.of(DischargeType.CLOUD_TO_CLOUD).cloudGlow());
    }

    @Test
    void aProfileScalesTheSharedTuningRatherThanReplacingIt() {
        LightningGenerationConfig base = LightningGenerationConfig.high();
        LightningGenerationConfig applied =
            DischargeProfiles.of(DischargeType.CLOUD_TO_CLOUD).geometry(base, 1f);

        assertEquals(base.generations(), applied.generations());
        assertEquals(base.roughness(), applied.roughness(), 1e-9);
        assertEquals(0, applied.canopyBranches(), "an aerial channel keeps no canopy");
        assertTrue(applied.branchProbability() <= 0.95);
    }

    @Test
    void anUnknownTypeFallsBackToTheOrdinaryFlash() {
        assertEquals(DischargeType.NEGATIVE_CLOUD_TO_GROUND, DischargeProfiles.of(null).type());
    }
}
