package org.telegram.messenger.extera;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Declarative capability allowlist shared by Extera's package-review UI.
 *
 * This class does not grant permissions or execute plugin code. A capability
 * becomes usable only after a future isolated runtime and broker explicitly
 * enforce the user's stored grant.
 */
public enum PluginCapability {
    UI_CHAT_LIST("ui:chat-list", Target.ANDROID),
    UI_CHAT_SCREEN("ui:chat-screen", Target.ANDROID),
    UI_PROFILE("ui:profile", Target.ANDROID),
    UI_PROFILE_BANNER("ui:profile-banner", Target.ANDROID),
    EVENTS_MESSAGE_METADATA("events:message-metadata", Target.ANDROID),
    EVENTS_PROFILE_PUBLIC("events:profile-public", Target.ANDROID),
    STORAGE_PLUGIN("storage:plugin", Target.ANDROID, Target.SERVER),
    NETWORK_HTTPS("network:https", Target.ANDROID, Target.SERVER),
    SERVER_MESSAGE_METADATA_HOOK("server:message-metadata-hook", Target.SERVER),
    SERVER_PROFILE_PUBLIC_HOOK("server:profile-public-hook", Target.SERVER);

    public enum Target {
        ANDROID,
        SERVER;

        public static Target fromManifest(String value) {
            if ("android".equals(value)) return ANDROID;
            if ("server".equals(value)) return SERVER;
            throw new IllegalArgumentException("Unsupported plugin target: " + value);
        }
    }

    private final String wireName;
    private final Set<Target> targets;

    PluginCapability(String wireName, Target... targets) {
        this.wireName = wireName;
        this.targets = Collections.unmodifiableSet(
                new HashSet<>(Arrays.asList(targets))
        );
    }

    public String wireName() {
        return wireName;
    }

    public boolean supports(Target target) {
        return targets.contains(target);
    }

    private static final Map<String, PluginCapability> BY_WIRE_NAME;
    static {
        Map<String, PluginCapability> values = new HashMap<>();
        for (PluginCapability capability : values()) {
            values.put(capability.wireName, capability);
        }
        BY_WIRE_NAME = Collections.unmodifiableMap(values);
    }

    public static PluginCapability fromWireName(String value) {
        return BY_WIRE_NAME.get(value);
    }

    public static boolean isAllowed(String value, Target target) {
        PluginCapability capability = fromWireName(value);
        return capability != null && capability.supports(target);
    }
}
