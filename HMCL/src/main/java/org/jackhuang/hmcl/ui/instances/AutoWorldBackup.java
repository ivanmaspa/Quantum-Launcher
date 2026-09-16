/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Creates world backups in the instance backups directory before an update modifies the instance.
@NotNullByDefault
public final class AutoWorldBackup {

    private AutoWorldBackup() {
    }

    /// Builds a best-effort task that backs up all worlds of the instance into the instance
    /// backups directory, reusing the file layout produced by `WorldBackupTask`.
    ///
    /// Worlds that are locked by a running game or cannot be compressed are logged and skipped,
    /// so the returned task never fails and never blocks the update that follows it.
    ///
    /// @param instance the instance whose worlds will be backed up
    /// @return a runnable task that writes backups into `instance.getBackupsDirectory()`
    public static Task<?> createBackupIfNeeded(HMCLGameInstance instance) {
        return createBackupIfNeeded(instance.getSavesDirectory(), instance.getBackupsDirectory());
    }

    /// Builds a best-effort task that backs up every world from `savesDirectory` into `backupsDirectory`.
    ///
    /// Worlds that are locked by a running game or cannot be compressed are logged and skipped,
    /// so the returned task never fails and never blocks the update that follows it.
    ///
    /// @param savesDirectory   the directory holding the world folders
    /// @param backupsDirectory the directory the backup zip files are written to
    /// @return a runnable task that creates the backups
    public static Task<?> createBackupIfNeeded(Path savesDirectory, Path backupsDirectory) {
        return Task.composeAsync(Schedulers.io(), () -> {
            if (!Files.isDirectory(savesDirectory)) {
                return Task.supplyAsync(() -> null);
            }
            return backupWorlds(backupsDirectory, World.getWorlds(savesDirectory));
        }).setSignificance(Task.TaskSignificance.MODERATE);
    }

    /// Builds a best-effort task that backs up the given worlds sequentially.
    ///
    /// Worlds that are locked by a running game or cannot be compressed are logged and skipped,
    /// so the returned task never fails. The task result is the number of failed backups.
    ///
    /// @param backupsDirectory the directory the backup zip files are written to
    /// @param worldsToBackup   the worlds to back up
    /// @return a runnable task whose result is the count of failed backups
    public static Task<Integer> backupWorlds(Path backupsDirectory, List<World> worldsToBackup) {
        return Task.supplyAsync(i18n("quantum.worldbackup.processing"), Schedulers.io(), () -> {
            int failed = 0;
            for (World world : worldsToBackup) {
                try {
                    new WorldBackupTask(world, backupsDirectory, true).run();
                } catch (Throwable e) {
                    failed++;
                    LOG.warning("Failed to create automatic backup for world " + world.getFileName(), e);
                }
            }
            return failed;
        }).setSignificance(Task.TaskSignificance.MODERATE);
    }
}