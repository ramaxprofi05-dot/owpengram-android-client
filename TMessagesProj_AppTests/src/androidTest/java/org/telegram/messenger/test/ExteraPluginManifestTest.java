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
                PluginManifest.parse(manifest("dist/main.js", "[\\"ui:profile\\",\\"ui:profile\\"]"))
        );
        String serverManifest = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\\"target\\":\\"android\\"", "\\"target\\":\\"server\\"");
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
                .replace("\\"permissions\\":[]", "\\"permissions\\":[],\\"sha256\\":\\"not-a-hash\\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(withBadHash.getBytes(StandardCharsets.UTF_8))
        );

        String withBadSignature = new String(manifest("dist/main.js", "[]"), StandardCharsets.UTF_8)
                .replace("\\"permissions\\":[]", "\\"permissions\\":[],\\"signature\\":\\"%%%\\"");
        assertThrows(IllegalArgumentException.class, () ->
                PluginManifest.parse(withBadSignature.getBytes(StandardCharsets.UTF_8))
        );
    }

}
