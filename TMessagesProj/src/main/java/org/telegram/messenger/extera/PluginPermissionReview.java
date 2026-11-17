package org.telegram.messenger.extera;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Computes the permission delta shown before installing or updating a plugin.
 * New capabilities must be reviewed and accepted again; removed capabilities
 * are never carried forward as grants.
 */
public final class PluginPermissionReview {
    private final Set<String> requested;
    private final Set<String> newlyRequested;
    private final Set<String> noLongerRequested;

    private PluginPermissionReview(
            Set<String> requested,
            Set<String> newlyRequested,
            Set<String> noLongerRequested
    ) {
        this.requested = immutableSorted(requested);
        this.newlyRequested = immutableSorted(newlyRequested);
        this.noLongerRequested = immutableSorted(noLongerRequested);
    }

    public static PluginPermissionReview compare(
            PluginCapability.Target target,
            Set<String> installedPermissions,
            Set<String> requestedPermissions
    ) {
        if (target == null) {
            throw new IllegalArgumentException("Plugin target is required");
        }
        Set<String> installed = installedPermissions == null
                ? Collections.<String>emptySet() : installedPermissions;
        Set<String> requested = requestedPermissions == null
                ? Collections.<String>emptySet() : requestedPermissions;

        Set<String> validated = new TreeSet<>();
        for (String permission : requested) {
            if (!PluginCapability.isAllowed(permission, target)) {
                throw new IllegalArgumentException(
                        "Unknown or target-incompatible plugin capability: " + permission
                );
            }
            if (!validated.add(permission)) {
                throw new IllegalArgumentException("Duplicate plugin capability: " + permission);
            }
        }
        for (String permission : installed) {
            if (!PluginCapability.isAllowed(permission, target)) {
                throw new IllegalArgumentException(
                        "Installed permission is not valid for target: " + permission
                );
            }
        }

        Set<String> added = new TreeSet<>(validated);
        added.removeAll(installed);
        Set<String> removed = new TreeSet<>(installed);
        removed.removeAll(validated);
        return new PluginPermissionReview(validated, added, removed);
    }

    private static Set<String> immutableSorted(Set<String> source) {
        return Collections.unmodifiableSet(new TreeSet<>(source));
    }

    public Set<String> requested() {
        return requested;
    }

    /** These permissions require a fresh, explicit user decision. */
    public Set<String> newlyRequested() {
        return newlyRequested;
    }

    /** These grants should be dropped when the update is committed. */
    public Set<String> noLongerRequested() {
        return noLongerRequested;
    }

    public boolean requiresRenewedConsent() {
        return !newlyRequested.isEmpty();
    }
}
