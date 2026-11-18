package org.telegram.messenger.extera;

import android.content.Context;
import android.util.AtomicFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Atomic app-private persistence for Extera plugin installation metadata.
 *
 * The registry never stores or executes plugin archives. Records loaded from
 * disk are deliberately marked unverified; the package verifier must check
 * each stored archive again before the runtime may enable it.
 */
public final class PluginRegistry {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_PLUGINS = 64;
    private static final int MAX_REGISTRY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JSON_NESTING = 32;

    private final AtomicFile file;

    public PluginRegistry(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required");
        File directory = new File(context.getNoBackupFilesDir(), "extera");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create Extera registry directory");
        }
        file = new AtomicFile(new File(directory, "plugins.json"));
    }

    /** Returns a stable, immutable snapshot. Loaded records require re-verification. */
    public synchronized Map<String, PluginInstallation> load() {
        File base = file.getBaseFile();
        if (!base.exists()) return Collections.emptyMap();

        try (FileInputStream input = file.openRead()) {
            byte[] bytes = readBounded(input, MAX_REGISTRY_BYTES);
            String jsonText = new String(bytes, StandardCharsets.UTF_8);
            if (!java.util.Arrays.equals(bytes, jsonText.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Registry is not valid UTF-8");
            }
            rejectDuplicateKeys(jsonText);
            JsonElement parsed = JsonParser.parseString(jsonText);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Registry root must be an object");

            JsonObject root = parsed.getAsJsonObject();
            requireOnlyKeys(root, "schema", "plugins");
            if (!root.has("schema") || !root.get("schema").isJsonPrimitive()
                    || !root.get("schema").getAsJsonPrimitive().isNumber()
                    || root.get("schema").getAsInt() != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported plugin registry schema");
            }
            JsonElement pluginsElement = root.get("plugins");
            if (pluginsElement == null || !pluginsElement.isJsonArray()) {
                throw new IllegalArgumentException("Registry plugins must be an array");
            }
            JsonArray plugins = pluginsElement.getAsJsonArray();
            if (plugins.size() > MAX_PLUGINS) throw new IllegalArgumentException("Too many installed plugins");

            Map<String, PluginInstallation> result = new TreeMap<>();
            for (JsonElement element : plugins) {
                if (element == null || !element.isJsonObject()) {
                    throw new IllegalArgumentException("Invalid plugin registry entry");
                }
                JsonObject record = element.getAsJsonObject();
                requireOnlyKeys(record, "manifest", "state", "grants");
                JsonElement manifestElement = record.get("manifest");
                JsonElement stateElement = record.get("state");
                JsonElement grantsElement = record.get("grants");
                if (manifestElement == null || !manifestElement.isJsonObject()
                        || stateElement == null || !stateElement.isJsonPrimitive()
                        || !stateElement.getAsJsonPrimitive().isString()
                        || grantsElement == null || !grantsElement.isJsonArray()) {
                    throw new IllegalArgumentException("Incomplete plugin registry entry");
                }

                PluginManifest manifest = PluginManifest.parse(
                        manifestElement.toString().getBytes(StandardCharsets.UTF_8)
                );
                PluginInstallation.State state = PluginInstallation.State.valueOf(stateElement.getAsString());
                if (state == PluginInstallation.State.UNINSTALLED) {
                    throw new IllegalArgumentException("Uninstalled plugins must not be stored");
                }

                Set<String> grants = new TreeSet<>();
                for (JsonElement grant : grantsElement.getAsJsonArray()) {
                    if (grant == null || !grant.isJsonPrimitive()
                            || !grant.getAsJsonPrimitive().isString()
                            || !grants.add(grant.getAsString())) {
                        throw new IllegalArgumentException("Invalid or duplicate plugin grant");
                    }
                }
                PluginInstallation installation = PluginInstallation.restorePersisted(manifest, grants, state);
                if (result.put(manifest.id, installation) != null) {
                    throw new IllegalArgumentException("Duplicate plugin id in registry: " + manifest.id);
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read Extera plugin registry", error);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Extera plugin registry is invalid", error);
        }
    }

    /** Atomically replaces the registry with the supplied installed records. */
    public synchronized void save(Collection<PluginInstallation> installations) {
        if (installations == null) throw new IllegalArgumentException("Plugin collection is required");
        if (installations.size() > MAX_PLUGINS) throw new IllegalArgumentException("Too many installed plugins");

        Map<String, PluginInstallation> sorted = new TreeMap<>();
        for (PluginInstallation installation : installations) {
            if (installation == null || installation.state == PluginInstallation.State.UNINSTALLED) {
                throw new IllegalArgumentException("Only installed or disabled plugins can be persisted");
            }
            if (sorted.put(installation.manifest.id, installation) != null) {
                throw new IllegalArgumentException("Duplicate plugin id: " + installation.manifest.id);
            }
        }

        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_VERSION);
        JsonArray plugins = new JsonArray();
        for (PluginInstallation installation : sorted.values()) {
            JsonObject record = new JsonObject();
            record.add("manifest", manifestJson(installation.manifest));
            record.addProperty("state", installation.state.name());
            JsonArray grants = new JsonArray();
            for (String permission : installation.grantedPermissions()) grants.add(permission);
            record.add("grants", grants);
            plugins.add(record);
        }
        root.add("plugins", plugins);

        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REGISTRY_BYTES) {
            throw new IllegalArgumentException("Plugin registry exceeds the maximum size");
        }

        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            output.flush();
            file.finishWrite(output);
        } catch (IOException error) {
            if (output != null) file.failWrite(output);
            throw new IllegalStateException("Unable to write Extera plugin registry", error);
        }
    }

    private static JsonObject manifestJson(PluginManifest manifest) {
        JsonObject json = new JsonObject();
        json.addProperty("id", manifest.id);
        json.addProperty("name", manifest.name);
        json.addProperty("version", manifest.version);
        json.addProperty("author", manifest.author);
        json.addProperty("description", manifest.description);
        json.addProperty("target", manifest.target == PluginCapability.Target.ANDROID ? "android" : "server");
        json.addProperty("entrypoint", manifest.entrypoint);
        JsonArray permissions = new JsonArray();
        for (String permission : new TreeSet<>(manifest.permissions)) permissions.add(permission);
        json.add("permissions", permissions);
        json.addProperty("sha256", manifest.sha256);
        json.addProperty("signer_key_id", manifest.signerKeyId);
        json.addProperty("signature", manifest.signature);
        return json;
    }

    private static void requireOnlyKeys(JsonObject object, String... allowed) {
        Set<String> keys = new TreeSet<>();
        Collections.addAll(keys, allowed);
        for (String key : object.keySet()) {
            if (!keys.contains(key)) throw new IllegalArgumentException("Unknown registry field: " + key);
        }
    }

    private static byte[] readBounded(FileInputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IllegalArgumentException("Plugin registry is too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void rejectDuplicateKeys(String json) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8))) {
            reader.setLenient(false);
            scanValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Registry must contain exactly one JSON value");
            }
        }
    }

    private static void scanValue(JsonReader reader, int depth) throws IOException {
        JsonToken token = reader.peek();
        if ((token == JsonToken.BEGIN_OBJECT || token == JsonToken.BEGIN_ARRAY)
                && depth >= MAX_JSON_NESTING) {
            throw new IllegalArgumentException("Registry JSON nesting is too deep");
        }
        switch (token) {
            case BEGIN_OBJECT:
                reader.beginObject();
                Set<String> names = new TreeSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw new IllegalArgumentException("Duplicate registry key: " + name);
                    scanValue(reader, depth + 1);
                }
                reader.endObject();
                return;
            case BEGIN_ARRAY:
                reader.beginArray();
                while (reader.hasNext()) scanValue(reader, depth + 1);
                reader.endArray();
                return;
            case STRING:
            case NUMBER:
                reader.nextString();
                return;
            case BOOLEAN:
                reader.nextBoolean();
                return;
            case NULL:
                reader.nextNull();
                return;
            default:
                throw new IllegalArgumentException("Unexpected registry JSON token: " + token);
        }
    }
}
