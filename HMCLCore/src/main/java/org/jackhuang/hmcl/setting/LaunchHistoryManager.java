/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Per-instance launch history storage backed by a JSON file.
///
/// All mutations are performed under a lock; file writes are performed off-thread
/// via a single daemon executor to prevent reordering.
///
@NotNullByDefault
public final class LaunchHistoryManager {

    /// Singleton instance.
    public static final LaunchHistoryManager INSTANCE = new LaunchHistoryManager();

    private static final Path STORAGE_FILE = OperatingSystem.getWorkingDirectory("hmcl").resolve("launch-history.json");

    private final Object lock = new Object();
    private final Map<String, LaunchHistory> data = new LinkedHashMap<>();
    private volatile boolean loaded;

    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Launch History Saver");
        t.setDaemon(true);
        return t;
    });

    private LaunchHistoryManager() {
    }

    /// Returns the launch history for the given instance, or an empty history if none has been recorded.
    public LaunchHistory get(String instanceId) {
        ensureLoaded();
        synchronized (lock) {
            return data.getOrDefault(instanceId, LaunchHistory.EMPTY);
        }
    }

    /// Records that a launch has started for the given instance.
    /// Increments the launch count and records the start timestamp.
    public void recordLaunchStart(String instanceId) {
        ensureLoaded();
        long now = System.currentTimeMillis();
        synchronized (lock) {
            LaunchHistory current = data.getOrDefault(instanceId, LaunchHistory.EMPTY);
            data.put(instanceId, new LaunchHistory(
                    now,
                    current.totalPlayMs(),
                    current.launchCount() + 1,
                    current.lastCrash(),
                    current.lastChange()));
        }
        scheduleSave();
    }

    /// Records that a launch has ended for the given instance.
    /// Accumulates play time. If exitCode != 0, updates the crash timestamp.
    public void recordLaunchEnd(String instanceId, int exitCode) {
        ensureLoaded();
        long now = System.currentTimeMillis();
        Instant crashInstant = exitCode != 0 ? Instant.now() : null;
        synchronized (lock) {
            LaunchHistory current = data.getOrDefault(instanceId, LaunchHistory.EMPTY);
            long addedMs = now > current.lastLaunch() ? now - current.lastLaunch() : 0L;
            data.put(instanceId, new LaunchHistory(
                    current.lastLaunch(),
                    current.totalPlayMs() + addedMs,
                    current.launchCount(),
                    crashInstant != null ? crashInstant : current.lastCrash(),
                    current.lastChange()));
        }
        scheduleSave();
    }

    /// Records that the given instance has been modified (e.g. mod installed/removed, settings changed).
    public void recordChange(String instanceId) {
        ensureLoaded();
        synchronized (lock) {
            LaunchHistory current = data.getOrDefault(instanceId, LaunchHistory.EMPTY);
            data.put(instanceId, new LaunchHistory(
                    current.lastLaunch(),
                    current.totalPlayMs(),
                    current.launchCount(),
                    current.lastCrash(),
                    Instant.now()));
        }
        scheduleSave();
    }

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            loadFromDisk();
            loaded = true;
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(STORAGE_FILE)) return;
        try {
            Map<String, LaunchHistory> map = JsonUtils.fromJsonFile(STORAGE_FILE, new TypeToken<Map<String, LaunchHistory>>() {});
            if (map != null) {
                data.putAll(map);
            }
        } catch (IOException e) {
            LOG.warning("Failed to load launch history", e);
        }
    }

    private void scheduleSave() {
        Map<String, LaunchHistory> snapshot;
        synchronized (lock) {
            snapshot = new LinkedHashMap<>(data);
        }
        SAVE_EXECUTOR.execute(() -> saveToDisk(snapshot));
    }

    private static void saveToDisk(Map<String, LaunchHistory> snapshot) {
        try {
            Files.createDirectories(STORAGE_FILE.getParent());
            JsonUtils.writeToJsonFile(STORAGE_FILE, snapshot);
        } catch (IOException e) {
            LOG.warning("Failed to save launch history", e);
        }
    }
}
