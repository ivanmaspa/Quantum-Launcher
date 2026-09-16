/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Stores Quantum-specific metadata, such as the last version whose changelog was shown to the user.
///
/// Persists the data as a JSON file alongside the launcher settings.
@NotNullByDefault
public final class QuantumMeta {

    /// The JSON file storing the metadata, located next to the launcher settings.
    private static final Path META_FILE = Metadata.HMCL_LOCAL_HOME.resolve("config").resolve("quantum-meta.json");

    /// The JSON property name for the last seen version string.
    private static final String PROPERTY_LAST_SEEN_VERSION = "lastSeenVersion";

    /// The singleton instance of the quantum metadata store.
    public static final QuantumMeta INSTANCE = new QuantumMeta();

    /// The last launcher version whose changelog was shown to the user.
    private @Nullable String lastSeenVersion;

    /// Loads the persisted metadata from disk.
    private QuantumMeta() {
        load();
    }

    /// Returns the last launcher version whose changelog was shown, or `null` if unknown.
    public @Nullable String getLastSeenVersion() {
        return lastSeenVersion;
    }

    /// Sets the last seen launcher version in memory.
    ///
    /// @param version the version string to store
    public void setLastSeenVersion(String version) {
        lastSeenVersion = version;
    }

    /// Returns whether the "What's new" dialog should be shown for the current version.
    public static boolean shouldShowWhatNew() {
        String lastSeen = INSTANCE.getLastSeenVersion();
        return lastSeen == null || !lastSeen.equals(Metadata.VERSION);
    }

    /// Marks the current launcher version as seen so the "What's new" dialog is not shown again.
    public static void markSeen() {
        INSTANCE.setLastSeenVersion(Metadata.VERSION);
        INSTANCE.save();
    }

    /// Loads the metadata from the JSON file if it exists.
    private void load() {
        if (!Files.isRegularFile(META_FILE)) {
            return;
        }

        try {
            @Nullable JsonObject object = JsonUtils.fromJsonFile(META_FILE, JsonObject.class);
            lastSeenVersion = JsonUtils.getString(object, PROPERTY_LAST_SEEN_VERSION);
        } catch (IOException e) {
            LOG.warning("Failed to load quantum meta: " + META_FILE, e);
        }
    }

    /// Saves the metadata to the JSON file.
    private void save() {
        try {
            Files.createDirectories(META_FILE.getParent());
            JsonObject object = new JsonObject();
            object.addProperty(PROPERTY_LAST_SEEN_VERSION, lastSeenVersion);
            JsonUtils.writeToJsonFile(META_FILE, object);
        } catch (IOException e) {
            LOG.warning("Failed to save quantum meta: " + META_FILE, e);
        }
    }
}
