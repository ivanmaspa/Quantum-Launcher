/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AddonCheckUpdatesTask<T extends LocalAddonFile> extends Task<List<LocalAddonFile.AddonUpdate>> {
    private final DownloadProvider downloadProvider;
    private final List<Task<LocalAddonFile.AddonUpdate>> dependents;
    private final @UnmodifiableView Collection<? extends Task<?>> allDependents;

    public AddonCheckUpdatesTask(DownloadProvider downloadProvider, String gameVersion, Collection<T> addons) {
        this.downloadProvider = downloadProvider;
        dependents = addons.stream().map(addon ->
                Task.supplyAsync(Schedulers.io(), () -> {
                    LocalAddonFile.AddonUpdate candidate = null;
                    for (RemoteAddon.Source source : RemoteAddon.Source.values()) {
                        LocalAddonFile.AddonUpdate update = null;
                        try {
                            update = addon.checkUpdates(downloadProvider, gameVersion, source);
                        } catch (IOException e) {
                            LOG.warning(String.format("Cannot check update for addon %s.", addon.getFileName()), e);
                        }
                        if (update == null) {
                            continue;
                        }

                        if (candidate == null || candidate.targetVersion().datePublished().isBefore(update.targetVersion().datePublished())) {
                            candidate = update;
                        }
                    }

                    return candidate;
                }).setName(addon.getFileName()).setSignificance(TaskSignificance.MAJOR).withCounter("update.checking")
        ).toList();

        setStage("update.checking");
        getProperties().put("total", dependents.size());
        allDependents = buildAllDependents(addons);
    }

    /// Schedules a best-effort world backup next to the update checks, so that a backup exists
    /// by the time the user replaces the addon files. The backup task does not contribute to the result.
    ///
    /// @param addons the addons being checked, used to locate the run directory from their file paths
    /// @return the update-check tasks together with the backup task, or the checks alone when unlocatable
    private @UnmodifiableView Collection<? extends Task<?>> buildAllDependents(Collection<T> addons) {
        Path runDirectory = findRunDirectory(addons);
        if (runDirectory == null) {
            return Collections.unmodifiableList(dependents);
        }

        List<Task<?>> all = new ArrayList<>(dependents.size() + 1);
        all.addAll(dependents);
        all.add(AutoWorldBackup.createBackupIfNeeded(runDirectory.resolve("saves"), runDirectory.resolve("backups")));
        return Collections.unmodifiableList(all);
    }

    /// Resolves the run directory that owns the addons from the first addon file path.
    ///
    /// @param addons the addons to inspect
    /// @return the run directory, or `null` when no addon file path can be located
    private static <T extends LocalAddonFile> @Nullable Path findRunDirectory(Collection<T> addons) {
        for (T addon : addons) {
            Path parent = addon.getFile().getParent();
            if (parent != null && parent.getParent() != null) {
                return parent.getParent();
            }
        }
        return null;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() {
        notifyPropertiesChanged();
    }

    @Override
    public Collection<? extends Task<?>> getDependents() {
        return allDependents;
    }

    @Override
    public boolean isRelyingOnDependents() {
        return false;
    }

    @Override
    public void execute() throws Exception {
        setResult(dependents.stream()
                .map(Task::getResult)
                .filter(Objects::nonNull).toList());
    }
}
