package org.telegram.messenger.extera;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, side-effect-free model of one installed Extera package.
 *
 * This is lifecycle policy only: it does not write files, verify signatures,
 * load entrypoints, or execute plugin code. Callers must verify the package
 * with the trusted publisher store before creating or updating this model.
 */
public final class PluginInstallation {
    public enum State {
        INSTALLED,
        DISABLED,
        UNINSTALLED
    }

    public final PluginManifest manifest;
    private final Set<String> grantedPermissions;
    public final State state;

    private PluginInstallation(
            PluginManifest manifest,
            Set<String> grantedPermissions,
            State state
    ) {
        if (manifest == null || state == null) {
            throw new IllegalArgumentException("Manifest and installation state are required");
        }
        this.manifest = manifest;
        this.grantedPermissions = immutableSorted(grantedPermissions);
        this.state = state;
    }

    public static PluginInstallation install(
            PluginManifest manifest,
            Set<String> approvedPermissions,
            boolean packageVerified
    ) {
        requireVerifiedInstallable(manifest, packageVerified);
        Set<String> approved = validatedGrants(manifest, approvedPermissions);
        return new PluginInstallation(manifest, approved, State.INSTALLED);
    }

    public PluginInstallation disable() {
        requireInstalled();
        if (state == State.DISABLED) return this;
        return new PluginInstallation(manifest, grantedPermissions, State.DISABLED);
    }

    public PluginInstallation enable() {
        requireInstalled();
        if (state == State.INSTALLED) return this;
        return new PluginInstallation(manifest, grantedPermissions, State.INSTALLED);
    }

    /**
     * Applies a verified update. Existing grants are retained only when still
     * requested; every newly requested capability must appear in the explicit
     * approval set. Removed grants are dropped automatically.
     */
    public PluginInstallation update(
            PluginManifest updatedManifest,
            Set<String> approvedNewPermissions,
            boolean packageVerified
    ) {
        requireInstalled();
        requireVerifiedInstallable(updatedManifest, packageVerified);
        if (!manifest.id.equals(updatedManifest.id)
                || manifest.target != updatedManifest.target) {
            throw new IllegalArgumentException("Plugin identity and target cannot change during update");
        }
        if (manifest.version.equals(updatedManifest.version)) {
            throw new IllegalArgumentException("Update must use a different plugin version");
        }

        PluginPermissionReview review = PluginPermissionReview.compare(
                updatedManifest.target, grantedPermissions, updatedManifest.permissions
        );
        Set<String> approved = approvedNewPermissions == null
                ? Collections.<String>emptySet() : approvedNewPermissions;
        for (String permission : approved) {
            if (!review.newlyRequested().contains(permission)) {
                throw new IllegalArgumentException("Approval contains an unrequested capability: " + permission);
            }
        }
        if (!approved.containsAll(review.newlyRequested())) {
            throw new IllegalArgumentException("New plugin capabilities require explicit approval");
        }

        return new PluginInstallation(updatedManifest, review.requested(), state);
    }

    public PluginInstallation uninstall() {
        if (state == State.UNINSTALLED) return this;
        return new PluginInstallation(manifest, Collections.<String>emptySet(), State.UNINSTALLED);
    }

    public Set<String> grantedPermissions() {
        return grantedPermissions;
    }

    private void requireInstalled() {
        if (state == State.UNINSTALLED) {
            throw new IllegalStateException("Plugin has been uninstalled");
        }
    }

    private static void requireVerifiedInstallable(PluginManifest manifest, boolean verified) {
        if (!verified) {
            throw new SecurityException("Plugin package has not been verified by the trusted package verifier");
        }
        if (manifest == null || !manifest.isInstallable()) {
            throw new IllegalArgumentException("Plugin manifest is not installable");
        }
    }

    private static Set<String> validatedGrants(PluginManifest manifest, Set<String> grants) {
        Set<String> result = new TreeSet<>();
        if (grants == null) return Collections.unmodifiableSet(result);
        for (String permission : grants) {
            if (permission == null || !manifest.permissions.contains(permission)
                    || !PluginCapability.isAllowed(permission, manifest.target)) {
                throw new IllegalArgumentException("Cannot grant an unrequested or unsupported capability: " + permission);
            }
            result.add(permission);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> immutableSorted(Set<String> source) {
        return Collections.unmodifiableSet(new TreeSet<>(source));
    }
}
