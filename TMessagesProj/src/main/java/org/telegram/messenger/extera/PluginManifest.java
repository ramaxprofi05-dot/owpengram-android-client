package org.telegram.messenger.extera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict parser for the shared Extera plugin manifest contract.
 *
 * Package signatures and archive digests must still be verified by the
 * trusted package verifier before installation. Parsing is not trust.
 */
public final class PluginManifest {
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;

    private static final Pattern ID = Pattern.compile("^[a-z0-9]+(?:[.-][a-z0-9]+){2,}$");
    private static final Pattern SEMVER = Pattern.compile(
            "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$"
    );
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern KEY_ID = Pattern.compile("^[a-zA-Z0-9._-]{8,96}$");
    private static final Set<String> FIELDS;
    static {
        Set<String> fields = new HashSet<>();
        Collections.addAll(fields, "id", "name", "version", "author", "description",
                "target", "entrypoint", "permissions", "sha256", "signer_key_id", "signature");
        FIELDS = Collections.unmodifiableSet(fields);
    }

    public final String id;
    public final String name;
    public final String version;
    public final String author;
    public final String description;
    public final PluginCapability.Target target;
    public final String entrypoint;
    public final Set<String> permissions;
    public final String sha256;
    public final String signerKeyId;
    public final String signature;

    private PluginManifest(JsonObject json) {
        id = string(json, "id", true);
        name = string(json, "name", true);
        version = string(json, "version", true);
        author = string(json, "author", true);
        description = string(json, "description", true);
        target = PluginCapability.Target.fromManifest(string(json, "target", true));
        entrypoint = string(json, "entrypoint", true);
        sha256 = string(json, "sha256", false);
        signerKeyId = string(json, "signer_key_id", false);
        signature = string(json, "signature", false);

        JsonElement rawPermissions = json.get("permissions");
        if (rawPermissions == null || !rawPermissions.isJsonArray()) {
            throw new IllegalArgumentException("permissions must be an array");
        }
        Set<String> parsed = new HashSet<>();
        for (JsonElement item : rawPermissions.getAsJsonArray()) {
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("each permission must be a string");
            }
            String permission = item.getAsString();
            if (!parsed.add(permission)) {
                throw new IllegalArgumentException("duplicate plugin capability: " + permission);
            }
            if (!PluginCapability.isAllowed(permission, target)) {
                throw new IllegalArgumentException("unknown or incompatible plugin capability: " + permission);
            }
        }
        permissions = Collections.unmodifiableSet(parsed);
        validate();
    }

    public static PluginManifest parse(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("plugin manifest size is invalid");
        }
        // Reject malformed UTF-8 instead of silently replacing invalid bytes.
        String jsonText = new String(data, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(data, jsonText.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("plugin manifest must be valid UTF-8");
        }
        JsonElement root = JsonParser.parseString(jsonText);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("plugin manifest must be a JSON object");
        }
        JsonObject object = root.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!FIELDS.contains(key)) {
                throw new IllegalArgumentException("unknown plugin manifest field: " + key);
            }
        }
        return new PluginManifest(object);
    }

    public boolean isInstallable() {
        return SHA256.matcher(sha256).matches()
                && KEY_ID.matcher(signerKeyId).matches()
                && isBase64Ed25519Signature(signature);
    }

    private void validate() {
        require(ID.matcher(id).matches() && id.length() <= 128,
                "plugin id must be a reverse-domain identifier");
        require(validText(name, 1, 64), "plugin name must be 1-64 characters");
        require(SEMVER.matcher(version).matches(), "plugin version must use semantic versioning");
        require(validText(author, 1, 64), "plugin author must be 1-64 characters");
        require(validText(description, 1, 512), "plugin description must be 1-512 characters");
        require(safeEntrypoint(entrypoint, target), "plugin entrypoint is unsafe for its target");
        require(sha256.isEmpty() || SHA256.matcher(sha256).matches(), "invalid sha256");
        require(signerKeyId.isEmpty() || KEY_ID.matcher(signerKeyId).matches(), "invalid signer_key_id");
        require(signature.isEmpty() || isBase64Ed25519Signature(signature), "invalid Ed25519 signature encoding");
    }

    private static boolean safeEntrypoint(String path, PluginCapability.Target target) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.indexOf('\\') >= 0
                || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0) {
            return false;
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) return false;
        }
        if (PluginCapability.Target.ANDROID == target) {
            return path.endsWith(".js") || path.endsWith(".mjs");
        }
        return path.endsWith(".wasm");
    }

    private static boolean validText(String value, int min, int max) {
        if (value == null) return false;
        String trimmed = value.trim();
        int count = trimmed.codePointCount(0, trimmed.length());
        if (count < min || count > max) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\\t') return false;
        }
        return true;
    }

    private static boolean isBase64Ed25519Signature(String value) {
        try {
            return android.util.Base64.decode(value, android.util.Base64.DEFAULT).length == 64;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String string(JsonObject object, String key, boolean required) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) throw new IllegalArgumentException("missing manifest field: " + key);
            return "";
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("manifest field must be a string: " + key);
        }
        return value.getAsString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
