/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  contributors
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
package org.jackhuang.hmcl.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Integration with the unofficial [mcpelauncher](https://minecraft-linux.github.io) Bedrock runtime.
///
/// The runtime is installed system-wide (e.g. the AUR packages `mcpelauncher-linux` plus
/// `mcpelauncher-ui`); this manager only needs to locate its binaries and data directory.
/// Game versions downloaded by the runtime live in `$XDG_DATA_HOME/mcpelauncher/versions` and can
/// be launched with `mcpelauncher-client -dg <version directory>`.
@NotNullByDefault
public final class BedrockManager {

    /// The public version database used by mcpelauncher itself; one file per Android ABI.
    private static final String VERSION_DB_URL =
            "https://raw.githubusercontent.com/minecraft-linux/mcpelauncher-versiondb/master/versions.%s.json.min";

    private static final String APP_DIR_NAME = "mcpelauncher";
    private static final String CLIENT_BINARY = "mcpelauncher-client";
    private static final String UI_BINARY = "mcpelauncher-ui-qt";
    private static final String GAME_LIBRARY = "libminecraftpe.so";

    private static final String @Unmodifiable [] ABIS = {"x86_64", "x86", "arm64-v8a", "armeabi-v7a"};

    /// Locations probed on top of the `PATH` for a system-wide mcpelauncher installation.
    private static final String @Unmodifiable [] KNOWN_BIN_DIRS = {
            "/opt/mcpelauncher-bin/bin/",
            "/usr/bin/",
            "/usr/local/bin/",
    };

    private BedrockManager() {
    }

    /// Returns the XDG data home directory (defaults to `~/.local/share`).
    ///
    /// @return the data home directory
    public static Path getDataHome() {
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg != null && !xdg.isEmpty()
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".local", "share");
    }

    /// Returns the directory where the runtime stores downloaded game versions.
    ///
    /// @return the versions directory
    public static Path getVersionsDirectory() {
        return getDataHome().resolve(APP_DIR_NAME).resolve("versions");
    }

    /// Returns the directory of a specific installed version.
    ///
    /// @param version the version directory name
    /// @return the version directory
    public static Path getVersionDirectory(String version) {
        return getVersionsDirectory().resolve(version);
    }

    /// Returns whether a game library exists inside the given version directory.
    ///
    /// @param versionDirectory the version directory
    /// @return true if the directory contains an extracted `libminecraftpe.so`
    private static boolean containsGameLibrary(Path versionDirectory) {
        for (String abi : ABIS) {
            if (Files.isRegularFile(versionDirectory.resolve("lib").resolve(abi).resolve(GAME_LIBRARY))) {
                return true;
            }
        }
        return false;
    }

    /// Returns the ABI sub-directory name used by mcpelauncher for the current platform.
    ///
    /// @return the ABI name, e.g. `x86_64`
    private static String getAbiDir() {
        Architecture arch = Architecture.CURRENT_ARCH;
        if (arch == Architecture.X86_64) {
            return "x86_64";
        }
        if (arch == Architecture.ARM64) {
            return "arm64-v8a";
        }
        if (arch == Architecture.X86) {
            return "x86";
        }
        return "armeabi-v7a";
    }

    /// Finds an executable by name on the `PATH` and the known binary directories.
    ///
    /// @param name the executable name
    /// @return the resolved executable path, or `null` when not found
    private static @Nullable Path findExecutable(String name) {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                Path candidate = Path.of(dir, name);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        for (String dir : KNOWN_BIN_DIRS) {
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /// Returns whether an executable is available on the `PATH`.
    ///
    /// @param name the executable name
    /// @return true if the executable is found
    private static boolean hasExecutable(String name) {
        return findExecutable(name) != null;
    }

    /// Returns the path of the `mcpelauncher-client` game launcher binary.
    ///
    /// @return the binary path, or `null` when the runtime is not installed
    public static @Nullable Path getClientPath() {
        return findExecutable(CLIENT_BINARY);
    }

    /// Returns the path of the `mcpelauncher-ui-qt` version manager binary.
    ///
    /// @return the binary path, or `null` when the version manager is not installed
    public static @Nullable Path getUIPath() {
        return findExecutable(UI_BINARY);
    }

    /// Returns whether the `mcpelauncher-client` runtime is installed and executable.
    ///
    /// @return true if the runtime is available
    public static boolean isClientInstalled() {
        return getClientPath() != null;
    }

    /// Returns whether the `mcpelauncher-ui-qt` version manager is installed.
    ///
    /// @return true if the version manager is available
    public static boolean isUIManagerInstalled() {
        return getUIPath() != null;
    }

    /// Returns the names of locally installed Bedrock versions.
    ///
    /// A version directory counts as installed when it contains a matching `libminecraftpe.so`.
    ///
    /// @return the installed version names, newest first
    public static @Unmodifiable List<String> getInstalledVersionNames() {
        List<String> result = new ArrayList<>();
        Path versions = getVersionsDirectory();
        if (!Files.isDirectory(versions)) {
            return List.of();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versions)) {
            for (Path dir : stream) {
                if (containsGameLibrary(dir)) {
                    result.add(dir.getFileName().toString());
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to scan Bedrock versions dir " + versions, e);
        }
        result.sort(Comparator.reverseOrder());
        return List.copyOf(result);
    }

    /// Returns whether a specific version is installed locally.
    ///
    /// @param version the version directory name
    /// @return true if the version directory contains a game library
    public static boolean isVersionInstalled(String version) {
        return containsGameLibrary(getVersionDirectory(version));
    }

    /// Loads the online list of Bedrock versions from the mcpelauncher version database.
    ///
    /// @return a task that yields the versions of the current platform, newest first
    public static Task<List<BedrockVersion>> refreshOnlineVersionsAsync() {
        return Task.supplyAsync(Schedulers.io(), () -> {
            String url = String.format(VERSION_DB_URL, getAbiDir());
            String json = HttpRequest.GET(url).getString();
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<BedrockVersion> versions = new ArrayList<>(array.size());
            for (var element : array) {
                JsonArray row = element.getAsJsonArray();
                if (row.size() < 2) {
                    continue;
                }
                int code = row.get(0).getAsInt();
                String name = row.get(1).getAsString();
                boolean beta = row.size() > 2 && row.get(2).getAsInt() == 1;
                versions.add(new BedrockVersion(code, name, beta));
            }
            versions.sort(Comparator.comparingInt(BedrockVersion::versionCode).reversed());
            return List.copyOf(versions);
        });
    }

    /// Launches an installed Bedrock version through the `mcpelauncher-client` runtime.
    ///
    /// The game data directory is intentionally left default so worlds created by the
    /// mcpelauncher version manager are shared with launches from this launcher.
    ///
    /// @param version the installed version directory name
    /// @throws IOException when the runtime is missing or the version directory is not found
    public static void launchVersion(String version) throws IOException {
        @Nullable Path client = getClientPath();
        if (client == null) {
            throw new IOException("mcpelauncher-client is not installed");
        }
        Path gameDir = getVersionDirectory(version);
        if (!Files.isDirectory(gameDir) || !containsGameLibrary(gameDir)) {
            throw new IOException("Game not found: " + gameDir);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(client.toString(), "-dg", gameDir.toString());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        processBuilder.start();
        LOG.info("Launching Bedrock version " + version + " with " + client);
    }

    /// Deletes an installed Bedrock version along with its game data.
    ///
    /// @param version the installed version directory name
    /// @throws IOException when the directory cannot be deleted
    public static void deleteVersion(String version) throws IOException {
        Path dir = getVersionDirectory(version);
        if (Files.exists(dir)) {
            FileUtils.deleteDirectory(dir);
        }
    }

    /// Opens the `mcpelauncher-ui-qt` version manager window.
    ///
    /// @throws IOException when the version manager is not installed
    public static void openUIManager() throws IOException {
        @Nullable Path ui = getUIPath();
        if (ui == null) {
            throw new IOException("mcpelauncher-ui-qt is not installed");
        }
        ProcessBuilder processBuilder = new ProcessBuilder(ui.toString());
        processBuilder.redirectErrorStream(true);
        processBuilder.start();
        LOG.info("Opening mcpelauncher-ui-qt at " + ui);
    }

    /// Returns an install command for the runtime on the current system, if one is available.
    ///
    /// The AUR packages are preferred because they carry prebuilt binaries.
    ///
    /// @return a command a terminal can execute, or `null` when no supported package manager exists
    public static @Nullable String getRuntimeInstallCommand() {
        if (hasExecutable("paru")) {
            return "paru -S --needed --noconfirm mcpelauncher-linux-bin mcpelauncher-ui-bin";
        }
        if (hasExecutable("yay")) {
            return "yay -S --needed --noconfirm mcpelauncher-linux mcpelauncher-ui";
        }
        if (hasExecutable("flatpak")) {
            return "flatpak install --user flathub io.mrarm.mcpelauncher";
        }
        return null;
    }
}