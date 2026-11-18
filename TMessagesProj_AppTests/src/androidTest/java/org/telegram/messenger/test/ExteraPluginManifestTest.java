package org.telegram.messenger.test;

import org.junit.Test;
import org.telegram.messenger.extera.PluginCapability;
import org.telegram.messenger.extera.PluginManifest;
import org.telegram.messenger.extera.PluginPermissionReview;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.*;

public class ExteraPluginManifestTest {
    private static byte[] manifest(String entrypoint, String permissions) {
        String json = "{"
                + "\"id\":\"dev.example.profile-tools\","
                + "\"name\":\"Profile Tools\","
                + "\"version\":\"1.0.0\","
                + "\"author\":\"Example Publisher\","
                + "\"description\":\"Profile presentation helpers\","
                + "\"target\":\"android\","
                + "\"entrypoint\":\"" + entrypoint + "\","
                + "\"permissions\":" + permissions
                + "}";
        return json.getBytes(StandardCharsets.UTF_8);
    }


    private static PluginManifest installableManifest(String version, String permissions) {
        String signature = "A".repeat(86) + "==";
        String json = new String(manifest("dist/main.js", permissions), StandardCharsets.UTF_8)
                .replace("\\\"version\\\":\\\"1.0.0\\\"", "\\\"version\\\":\\\"" + version + "\\\"")
                .replace("\\\"permissions\\\":" + permissions,
                        "\\\"permissions\\\":" + permissions + ","
                                + "\\\"sha256\\\":\\\"" + "a".repeat(64) + "\\","
"
                                + "\\\"signer_key_id\\\":\\\"publisher-key-0001\\\","
"
                                + "\\\"signature\\\":\\\"" + signature + "\\\"");
        return PluginManifest.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void acceptsKnownAndroidCapabilities() {
        PluginManifest parsed = PluginManifest.parse(
                manifest("dist/main.js", "[\"ui:profile\",\"ui:profile-banner\"]")
        );
        assertEquals("dev.example.profile-tools", parsed.id);
        assertTrue(parsed.permissions.contains("ui:profile-banner"));
        assertFalse(parsed.isInstallable());
    }

    @Test
    public void rejectsDuplicateJsonKeysIncludingEscapedAndNestedNames() {
        String base = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8);
        String nameField = "\"name\":\"Profile Tools\"";
        String duplicate = base.replace(nameField, nameField + ",\"name\":\"Overridden\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(duplicate.getBytes(StandardCharsets.UTF_8))
        );

        String escapedName = "\\" + "u006eame";
        String escapedDuplicate = base.replace(nameField,
                nameField + ",\"" + escapedName + "\":\"Overridden\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(escapedDuplicate.getBytes(StandardCharsets.UTF_8))
        );

        String nestedDuplicate = base.substring(0, base.length() - 1)
                + ",\"metadata\":{\"x\":1,\"x\":2}}";
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(nestedDuplicate.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    public void rejectsUnknownFieldsAndCapabilities() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(manifest("dist/main.js", "[\"ui:root-access\"]"))
        );
        byte[] withUnknown = (new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\"permissions\":[]", "\"permissions\":[],\"silent\":true"))
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> PluginManifest.parse(withUnknown));
    }

    @Test
    public void rejectsUnsafeEntrypoints() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(manifest("../outside.js", "[]"))
        );
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(manifest("dist\\\\main.js", "[]"))
        );
    }

    @Test
    public void permissionUpdateRequiresConsentForNewCapabilities() {
        PluginPermissionReview review = PluginPermissionReview.compare(
                PluginCapability.Target.ANDROID,
                new HashSet<>(Collections.singletonList("ui:profile")),
                new HashSet<>(Arrays.asList("ui:profile", "ui:profile-banner"))
        );
        assertTrue(review.requiresRenewedConsent());
        assertEquals(Collections.singleton("ui:profile-banner"), review.newlyRequested());
        assertTrue(review.noLongerRequested().isEmpty());
    }

    @Test
    public void permissionUpdateDropsRemovedCapabilities() {
        PluginPermissionReview review = PluginPermissionReview.compare(
                PluginCapability.Target.ANDROID,
                new HashSet<>(Arrays.asList("ui:profile", "events:profile-public")),
                Collections.singleton("ui:profile")
        );
        assertFalse(review.requiresRenewedConsent());
        assertEquals(Collections.singleton("events:profile-public"), review.noLongerRequested());
    }

    @Test
    public void rejectsDuplicateAndWrongTargetCapabilities() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(manifest("dist/main.js", "[\"ui:profile\",\"ui:profile\"]"))
        );
        String serverManifest = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\"target\":\"android\"", "\"target\":\"server\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(serverManifest.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    public void rejectsMalformedUtf8AndOversizedManifest() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(new byte[] {(byte) 0xc3, (byte) 0x28})
        );
        byte[] oversized = new byte[PluginManifest.MAX_MANIFEST_BYTES + 1];
        assertThrows(IllegalArgumentException.class, () -> PluginManifest.parse(oversized));
    }

    @Test
    public void rejectsMalformedIntegrityMetadata() {
        String withBadHash = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\"permissions\":[]", "\"permissions\":[],\"sha256\":\"not-a-hash\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(withBadHash.getBytes(StandardCharsets.UTF_8))
        );

        String withBadSignature = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\"permissions\":[]", "\"permissions\":[],\"signature\":\"%%%\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(withBadSignature.getBytes(StandardCharsets.UTF_8))
        );

        String validSignature = "A".repeat(86) + "==";
        String withNonCanonicalSignature = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\"permissions\":[]", "\"permissions\":[],\"signature\":\"" + validSignature + "\\n\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(withNonCanonicalSignature.getBytes(StandardCharsets.UTF_8))
        );
    }


    @Test
    public void permissionReviewRejectsInvalidPreviouslyInstalledGrants() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginPermissionReview.compare(
                        PluginCapability.Target.ANDROID,
                        Collections.singleton("server:message-metadata-hook"),
                        Collections.singleton("ui:profile")
                )
        );
    }

    @Test
    public void permissionReviewDoesNotTreatRemovedGrantsAsNewConsent() {
        PluginPermissionReview review = PluginPermissionReview.compare(
                PluginCapability.Target.ANDROID,
                new HashSet<>(Arrays.asList("ui:profile", "ui:profile-banner")),
                Collections.singleton("ui:profile")
        );
        assertFalse(review.requiresRenewedConsent());
        assertEquals(Collections.singleton("ui:profile"), review.requested());
        assertEquals(Collections.singleton("ui:profile-banner"), review.noLongerRequested());
    }

    @Test
    public void permissionReviewReturnsImmutableDeterministicSets() {
        PluginPermissionReview review = PluginPermissionReview.compare(
                PluginCapability.Target.ANDROID,
                Collections.<String>emptySet(),
                new HashSet<>(Arrays.asList("ui:profile-banner", "ui:profile"))
        );
        assertEquals(Arrays.asList("ui:profile", "ui:profile-banner"),
                Arrays.asList(review.requested().toArray(new String[0])));
        assertThrows(UnsupportedOperationException.class, () ->
                review.requested().add("network:https")
        );
        assertThrows(UnsupportedOperationException.class, () ->
                review.newlyRequested().clear()
        );
    }

    @Test
    public void permissionReviewRejectsNullCapabilityAsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginPermissionReview.compare(
                        PluginCapability.Target.ANDROID,
                        Collections.<String>emptySet(),
                        Collections.singleton(null)
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                PluginPermissionReview.compare(
                        PluginCapability.Target.ANDROID,
                        Collections.singleton(null),
                        Collections.<String>emptySet()
                )
        );
    }

    @Test
    public void permissionReviewRejectsMissingTarget() {
        assertThrows(IllegalArgumentException.class, () ->
                PluginPermissionReview.compare(
                        null,
                        Collections.<String>emptySet(),
                        Collections.singleton("ui:profile")
                )
        );
    }

    @Test
    public void acceptsCanonicalSignatureEncodingWithCompleteIntegrityMetadata() {
        String validSignature = "A".repeat(86) + "==";
        String signedManifest = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\"permissions\":[]",
                        "\"permissions\":[],"
                                + "\"sha256\":\"" + "a".repeat(64) + "\","
                                + "\"signer_key_id\":\"publisher-key-0001\","
                                + "\"signature\":\"" + validSignature + "\"");
        PluginManifest parsed = PluginManifest.parse(signedManifest.getBytes(StandardCharsets.UTF_8));
        assertTrue(parsed.isInstallable());
        assertEquals(validSignature, parsed.signature);
    }

    @Test
    public void rejectsExcessivelyNestedManifestJson() {
        String nested = "[".repeat(40) + "0" + "]".repeat(40);
        String json = "{\"id\":\"dev.example.profile-tools\","
                + "\"name\":\"Profile Tools\","
                + "\"version\":\"1.0.0\","
                + "\"author\":\"Example Publisher\","
                + "\"description\":\"Profile presentation helpers\","
                + "\"target\":\"android\","
                + "\"entrypoint\":\"dist/main.js\","
                + "\"permissions\":[],"
                + "\"metadata\":" + nested + "}";
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(json.getBytes(StandardCharsets.UTF_8))
        );
    }



    @Test
    public void installationRequiresVerifiedSignedPackageAndOnlyRequestedGrants() {
        PluginManifest manifest = installableManifest("1.0.0", "[\\\"ui:profile\\\"]");
        assertThrows(SecurityException.class, () ->
                PluginInstallation.install(manifest, Collections.singleton("ui:profile"), false)
        );
        assertThrows(IllegalArgumentException.class, () ->
                PluginInstallation.install(manifest, Collections.singleton("network:https"), true)
        );
        PluginInstallation installed = PluginInstallation.install(
                manifest, Collections.singleton("ui:profile"), true
        );
        assertEquals(PluginInstallation.State.INSTALLED, installed.state);
        assertEquals(Collections.singleton("ui:profile"), installed.grantedPermissions());
    }

    @Test
    public void updateRequiresConsentAndRevokesRemovedCapabilities() {
        PluginInstallation installed = PluginInstallation.install(
                installableManifest("1.0.0", "[\\\"ui:profile\\\"]"),
                Collections.singleton("ui:profile"), true
        );
        PluginManifest addsBanner = installableManifest(
                "1.1.0", "[\\\"ui:profile\\\",\\\"ui:profile-banner\\\"]"
        );
        assertThrows(IllegalArgumentException.class, () ->
                installed.update(addsBanner, Collections.<String>emptySet(), true)
        );
        PluginInstallation approved = installed.update(
                addsBanner, Collections.singleton("ui:profile-banner"), true
        );
        assertEquals(2, approved.grantedPermissions().size());

        PluginManifest removesBanner = installableManifest("1.2.0", "[\\\"ui:profile\\\"]");
        PluginInstallation cleaned = approved.update(
                removesBanner, Collections.<String>emptySet(), true
        );
        assertEquals(Collections.singleton("ui:profile"), cleaned.grantedPermissions());
    }

    @Test
    public void disableEnableAndUninstallHaveExplicitStates() {
        PluginInstallation installed = PluginInstallation.install(
                installableManifest("1.0.0", "[\\\"ui:profile\\\"]"),
                Collections.singleton("ui:profile"), true
        );
        PluginInstallation disabled = installed.disable();
        assertEquals(PluginInstallation.State.DISABLED, disabled.state);
        assertEquals(PluginInstallation.State.INSTALLED, disabled.enable().state);
        PluginInstallation removed = disabled.uninstall();
        assertEquals(PluginInstallation.State.UNINSTALLED, removed.state);
        assertTrue(removed.grantedPermissions().isEmpty());
        assertThrows(IllegalStateException.class, removed::enable);
    }

}
