/*
 * Quantum Launcher
 * Copyright (C) 2026 masik and contributors
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
package org.jackhuang.hmcl.diagnostics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionRange;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Analyzes the mods of a game instance and reports common problems such as duplicate mods,
/// missing dependencies, incompatible versions, conflicting mods, and unsuitable Java.
///
/// This class is independent of JavaFX so that it can be executed on a background thread and
/// its results can be rendered on the JavaFX thread.
@NotNullByDefault
public final class ModDiagnosis {

    private static final String SHA1 = "SHA-1";

    /// Mods provided by the game itself or by its mod loader rather than placed into the mods
    /// directory. They are handled separately from plain mods and never reported as missing.
    private static final Set<String> ENVIRONMENT_MOD_IDS = Set.of(
            "minecraft", "java", "fabricloader", "quilt-loader", "quilt_loader",
            "forge", "neoforge", "javafml", "fabric");

    /// Maps a dependency id to the mod loader that provides it.
    private static final Map<String, ModLoaderType> LOADER_ID_TO_TYPE = Map.of(
            "fabricloader", ModLoaderType.FABRIC,
            "quilt-loader", ModLoaderType.QUILT,
            "quilt_loader", ModLoaderType.QUILT,
            "forge", ModLoaderType.FORGE,
            "neoforge", ModLoaderType.NEO_FORGE,
            "javafml", ModLoaderType.FORGE);

    private static final Pattern MAVEN_RANGE = Pattern.compile("^([\\[(])\\s*(?<min>[^,]*?)\\s*,\\s*(?<max>[^)\\]\\[]*?)\\s*([)\\]])$");
    private static final Pattern CONSTRAINT = Pattern.compile("^(>=|<=|>|<|=|~)?\\s*(?<version>.+)$");
    private static final Pattern WILDCARD_VERSION = Pattern.compile("^(?<base>\\d+(?:\\.\\d+)*)\\.(?:x|X|\\*)$");

    private ModDiagnosis() {
    }

    /// A single diagnostic problem found while analyzing an instance's mods.
    ///
    /// @param level   severity of the problem
    /// @param message short localized description of the problem
    /// @param detail  optional localized detail, may be empty
    public record ModProblem(ModProblemLevel level, String message, String detail) {
    }

    /// Severity of a diagnosed problem.
    public enum ModProblemLevel {
        OK, INFO, WARNING, ERROR
    }

    /// Metadata extracted from a single active mod jar.
    private record ModInfo(
            LocalModFile file,
            @Nullable String sha1,
            @Unmodifiable Map<String, String> depends,
            @Unmodifiable Map<String, String> conflicts) {
    }

    /// Analyzes all active mods of the given instance and returns the detected problems.
    ///
    /// Scanning hashes and parsing the metadata of every mod jar can take a while, so this
    /// method should be invoked on a background thread.
    ///
    /// @param instance the game instance whose mods are analyzed
    /// @return the detected problems, possibly empty
    public static List<ModProblem> analyze(HMCLGameInstance instance) {
        List<ModProblem> problems = new ArrayList<>();

        List<LocalModFile> modFiles;
        try {
            modFiles = instance.getModManager().getLocalFiles();
        } catch (IOException e) {
            LOG.warning("Failed to read mods of instance " + instance.getId(), e);
            problems.add(new ModProblem(ModProblemLevel.ERROR,
                    i18n("quantum.diag.mods.failed"),
                    StringUtils.getStackTrace(e)));
            return List.copyOf(problems);
        }

        if (modFiles.isEmpty()) {
            return List.of();
        }

        List<ModInfo> mods = new ArrayList<>();
        for (LocalModFile file : modFiles) {
            mods.add(readModInfo(file));
        }

        @Nullable Integer javaMajorVersion = resolveJavaMajorVersion(instance);
        @Nullable String minecraftVersionString = resolveMinecraftVersion(instance);

        Map<String, Set<String>> versionsById = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            String id = mod.file().getId();
            if (StringUtils.isBlank(id)) {
                continue;
            }
            versionsById.computeIfAbsent(id, ignored -> new HashSet<>())
                    .add(mod.file().getVersion());
        }

        checkDuplicateHashes(mods, problems);
        checkDuplicateIds(mods, problems);
        checkMissingDependencies(mods, versionsById, problems);
        checkRuntimeRequirements(mods, javaMajorVersion, minecraftVersionString, loaderVersions(instance), problems);
        checkConflicts(mods, versionsById, problems);

        return List.copyOf(problems);
    }

    /// Resolves the parsed Java major version used to launch the instance, or `null` when it
    /// cannot be determined.
    private static @Nullable Integer resolveJavaMajorVersion(HMCLGameInstance instance) {
        GameSettings.Effective settings = instance.getEffectiveSettings();
        try {
            @Nullable JavaRuntime java = settings.getJava(instance.getVersion(), instance.getManifest());
            return java != null ? java.getParsedVersion() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warning("Interrupted while resolving Java for instance " + instance.getId(), e);
            return null;
        }
    }

    /// Reads the metadata of a single mod jar and computes its SHA-1 hash. A file that cannot
    /// be read is logged and kept with `null` fields so that hash-duplicate detection can still
    /// make sense of it.
    private static ModInfo readModInfo(LocalModFile file) {
        Map<String, String> depends = Collections.emptyMap();
        Map<String, String> conflicts = Collections.emptyMap();
        @Nullable String sha1 = null;

        try {
            sha1 = DigestUtils.digestToString(SHA1, file.getFile());
        } catch (IOException e) {
            LOG.warning("Failed to hash mod file " + file.getFile(), e);
        }

        try (ZipFileTree tree = CompressingUtils.openZipTree(file.getFile())) {
            ModLoaderType loaderType = file.getModLoaderType();
            if (loaderType == ModLoaderType.FABRIC || loaderType == ModLoaderType.LEGACY_FABRIC) {
                depends = readFabricJson(tree, "fabric.mod.json");
            } else if (loaderType == ModLoaderType.QUILT) {
                @Nullable ZipArchiveEntry quilt = tree.getEntry("quilt.mod.json");
                depends = quilt != null ? readFabricJson(tree, "quilt.mod.json") : readFabricJson(tree, "fabric.mod.json");
            } else if (loaderType == ModLoaderType.FORGE || loaderType == ModLoaderType.NEO_FORGE) {
                depends = readForgeToml(tree);
            }
        } catch (IOException e) {
            LOG.warning("Failed to parse metadata of mod file " + file.getFile(), e);
        }

        return new ModInfo(file, sha1, depends, conflicts);
    }

    /// Reads the `depends` map of a Fabric/Quilt mod metadata. Both plain strings and objects
    /// with a `version` field are accepted.
    private static @Unmodifiable Map<String, String> readFabricJson(ZipFileTree tree, String entryPath) {
        ZipArchiveEntry entry = tree.getEntry(entryPath);
        if (entry == null) {
            return Collections.emptyMap();
        }

        JsonObject root;
        try {
            String json = IOUtils.readFullyAsString(tree.getInputStream(entry), StandardCharsets.UTF_8);
            JsonElement element = JsonUtils.GSON.fromJson(json, JsonElement.class);
            if (!(element instanceof JsonObject object)) {
                return Collections.emptyMap();
            }
            root = object;
        } catch (IOException | JsonParseException e) {
            LOG.warning("Malformed mod metadata entry " + entryPath, e);
            return Collections.emptyMap();
        }

        return parseDependencyMap(root);
    }

    /// Converts a Fabric-like `depends` object into `id -> version range` pairs.
    private static @Unmodifiable Map<String, String> parseDependencyMap(JsonObject root) {
        Map<String, String> depends = new LinkedHashMap<>();
        JsonElement element = root.get("depends");
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                @Nullable String range = null;
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    range = value.getAsString();
                } else if (value.isJsonObject()) {
                    JsonElement version = value.getAsJsonObject().get("version");
                    if (version != null && version.isJsonPrimitive() && version.getAsJsonPrimitive().isString()) {
                        range = version.getAsString();
                    }
                }
                depends.put(entry.getKey(), range != null && !range.isBlank() ? range : "*");
            }
        }
        return Collections.unmodifiableMap(depends);
    }

    /// Reads the mandatory dependencies of a Forge/NeoForge mod from its `mods.toml`.
    private static @Unmodifiable Map<String, String> readForgeToml(ZipFileTree tree) {
        try {
            ZipArchiveEntry neoforge = tree.getEntry("META-INF/neoforge.mods.toml");
            ZipArchiveEntry forge = tree.getEntry("META-INF/mods.toml");
            ZipArchiveEntry entry = neoforge != null ? neoforge : forge;
            if (entry == null) {
                return Collections.emptyMap();
            }

            TomlParseResult toml = Toml.parse(tree.readTextEntry(entry));
            if (toml.hasErrors()) {
                LOG.warning("Malformed mod metadata entry " + entry.getName());
                return Collections.emptyMap();
            }

            Map<String, String> depends = new LinkedHashMap<>();

            @Nullable String modLoader = toml.getString("modLoader");
            @Nullable String loaderVersion = toml.getString("loaderVersion");
            if ("javafml".equals(modLoader) && StringUtils.isNotBlank(loaderVersion)) {
                depends.put("forge", loaderVersion);
            }

            TomlTable dependencies = toml.getTable("dependencies");
            if (dependencies != null) {
                for (String modId : dependencies.keySet()) {
                    TomlArray array = dependencies.getArray(modId);
                    if (array == null) {
                        continue;
                    }
                    for (Object element : array.toList()) {
                        if (!(element instanceof TomlTable dependency)) {
                            continue;
                        }
                        @Nullable String depId = dependency.getString("modId");
                        Boolean mandatory = dependency.getBoolean("mandatory");
                        @Nullable String versionRange = dependency.getString("versionRange");
                        if (StringUtils.isNotBlank(depId) && (mandatory == null || mandatory)
                                && StringUtils.isNotBlank(versionRange)) {
                            depends.merge(depId, versionRange, (a, b) -> a);
                        }
                    }
                }
            }

            return Collections.unmodifiableMap(depends);
        } catch (IOException e) {
            LOG.warning("Failed to read forge mod metadata", e);
            return Collections.emptyMap();
        }
    }

    /// Reports mod jars that are byte-identical, which almost always means that the same mod
    /// was placed into the mods folder twice under different file names.
    private static void checkDuplicateHashes(List<ModInfo> mods, List<ModProblem> problems) {
        Map<String, List<ModInfo>> byHash = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            if (mod.sha1() != null) {
                byHash.computeIfAbsent(mod.sha1(), ignored -> new ArrayList<>()).add(mod);
            }
        }

        for (List<ModInfo> duplicates : byHash.values()) {
            if (duplicates.size() < 2) {
                continue;
            }
            problems.add(new ModProblem(ModProblemLevel.WARNING,
                    i18n("quantum.diag.mod.duplicate_hash"),
                    i18n("quantum.diag.mod.duplicate_hash.detail", joinFileNames(duplicates))));
        }
    }

    /// Reports active mods that declare the same mod id.
    private static void checkDuplicateIds(List<ModInfo> mods, List<ModProblem> problems) {
        Map<String, List<ModInfo>> byId = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            String id = mod.file().getId();
            if (StringUtils.isBlank(id)) {
                continue;
            }
            byId.computeIfAbsent(id, ignored -> new ArrayList<>()).add(mod);
        }

        for (Map.Entry<String, List<ModInfo>> entry : byId.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            String id = entry.getKey();
            problems.add(new ModProblem(ModProblemLevel.WARNING,
                    i18n("quantum.diag.mod.duplicate_id", id),
                    i18n("quantum.diag.mod.duplicate_id.detail", joinFileNames(entry.getValue()))));
        }
    }

    /// Reports mods that require dependencies which are not installed or whose installed version
    /// does not satisfy the declared range.
    private static void checkMissingDependencies(
            List<ModInfo> mods,
            Map<String, Set<String>> versionsById,
            List<ModProblem> problems) {
        for (ModInfo mod : mods) {
            for (Map.Entry<String, String> entry : mod.depends().entrySet()) {
                String dependencyId = entry.getKey();
                if (ENVIRONMENT_MOD_IDS.contains(dependencyId)) {
                    continue;
                }
                Set<String> installedVersions = versionsById.get(dependencyId);
                if (installedVersions == null || installedVersions.isEmpty()) {
                    problems.add(new ModProblem(ModProblemLevel.ERROR,
                            i18n("quantum.diag.mod.missing_dependency", displayName(mod), dependencyId),
                            i18n("quantum.diag.mod.missing_dependency.detail", displayName(mod), dependencyId)));
                } else if (!containsAnyVersion(entry.getValue(), installedVersions)) {
                    problems.add(new ModProblem(ModProblemLevel.WARNING,
                            i18n("quantum.diag.mod.dependency_version", displayName(mod), dependencyId, entry.getValue(), String.join(", ", installedVersions)),
                            i18n("quantum.diag.mod.dependency_version.detail", displayName(mod), dependencyId, entry.getValue(), String.join(", ", installedVersions))));
                }
            }
        }
    }

    /// Reports mods whose declared Minecraft, mod loader, or Java requirements are incompatible
    /// with the runtime of the instance.
    private static void checkRuntimeRequirements(
            List<ModInfo> mods,
            @Nullable Integer javaMajorVersion,
            @Nullable String minecraftVersion,
            Map<ModLoaderType, String> loaderVersions,
            List<ModProblem> problems) {
        for (ModInfo mod : mods) {
            String modName = displayName(mod);
            for (Map.Entry<String, String> entry : mod.depends().entrySet()) {
                String dependencyId = entry.getKey();
                String range = entry.getValue();

                switch (dependencyId) {
                    case "minecraft" -> {
                        if (minecraftVersion != null && !containsVersion(range, minecraftVersion)) {
                            problems.add(new ModProblem(ModProblemLevel.ERROR,
                                    i18n("quantum.diag.mod.minecraft_version", modName, minecraftVersion, range),
                                    i18n("quantum.diag.mod.minecraft_version.detail", modName, minecraftVersion, range)));
                        }
                    }
                    case "java" -> {
                        if (javaMajorVersion == null) {
                            problems.add(new ModProblem(ModProblemLevel.ERROR,
                                    i18n("quantum.diag.mod.java_not_found", modName),
                                    i18n("quantum.diag.mod.java_not_found.detail", modName)));
                        } else if (!containsVersion(range, String.valueOf(javaMajorVersion))) {
                            problems.add(new ModProblem(ModProblemLevel.ERROR,
                                    i18n("quantum.diag.mod.java_version", modName, jvmVersionString(javaMajorVersion), range),
                                    i18n("quantum.diag.mod.java_version.detail", modName, jvmVersionString(javaMajorVersion), range)));
                        }
                    }
                    case "fabricloader", "quilt-loader", "quilt_loader", "forge", "neoforge" -> {
                        ModLoaderType loaderType = LOADER_ID_TO_TYPE.get(dependencyId);
                        String installed = loaderType != null ? loaderVersions.get(loaderType) : null;
                        String loaderName = loaderType != null ? loaderType.displayName() : dependencyId;
                        if (installed == null) {
                            problems.add(new ModProblem(ModProblemLevel.ERROR,
                                    i18n("quantum.diag.mod.loader_missing", modName, loaderName),
                                    i18n("quantum.diag.mod.loader_missing.detail", modName, loaderName)));
                        } else if (!containsVersion(range, installed)) {
                            problems.add(new ModProblem(ModProblemLevel.ERROR,
                                    i18n("quantum.diag.mod.loader_version", modName, loaderName, installed, range),
                                    i18n("quantum.diag.mod.loader_version.detail", modName, loaderName, installed, range)));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /// Reports active mods that conflict with other installed mods.
    private static void checkConflicts(List<ModInfo> mods, Map<String, Set<String>> versionsById, List<ModProblem> problems) {
        for (ModInfo mod : mods) {
            for (Map.Entry<String, String> entry : mod.conflicts().entrySet()) {
                String conflictingId = entry.getKey();
                Set<String> installedVersions = versionsById.get(conflictingId);
                if (installedVersions == null || installedVersions.isEmpty()) {
                    continue;
                }
                if (containsAnyVersion(entry.getValue(), installedVersions)) {
                    problems.add(new ModProblem(ModProblemLevel.ERROR,
                            i18n("quantum.diag.mod.conflict", displayName(mod), conflictingId),
                            i18n("quantum.diag.mod.conflict.detail", displayName(mod), conflictingId, entry.getValue())));
                }
            }
        }
    }

    /// Returns the installed versions of all supported mod loaders of the instance.
    private static @Unmodifiable Map<ModLoaderType, String> loaderVersions(HMCLGameInstance instance) {
        Map<ModLoaderType, String> result = new LinkedHashMap<>();
        for (GameComponentType componentType : GameComponentType.MOD_LOADERS) {
            @Nullable ModLoaderType loaderType = componentType.getModLoaderType();
            @Nullable String version = instance.getComponentVersion(componentType);
            if (loaderType != null && StringUtils.isNotBlank(version)) {
                result.put(loaderType, version);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /// Returns the display name of a mod, falling back to its id or its file name.
    private static String displayName(ModInfo mod) {
        if (StringUtils.isNotBlank(mod.file().getName())) {
            return mod.file().getName();
        }
        if (StringUtils.isNotBlank(mod.file().getId())) {
            return mod.file().getId();
        }
        return mod.file().getFileName();
    }

    /// Joins the file names of the given mods into a single string.
    private static String joinFileNames(List<ModInfo> mods) {
        return mods.stream().map(mod -> mod.file().getFileName()).collect(Collectors.joining(", "));
    }

    /// Whether any of the installed versions satisfies the given version range.
    private static boolean containsAnyVersion(String rangeString, Set<String> installedVersions) {
        if ("*".equals(rangeString)) {
            return true;
        }
        for (String installed : installedVersions) {
            if (containsVersion(rangeString, installed)) {
                return true;
            }
        }
        return false;
    }

    /// Whether the given version string satisfies the given Fabric/Maven-style range.
    private static boolean containsVersion(String rangeString, String versionString) {
        VersionRange<VersionNumber> range = parseVersionRange(rangeString);
        try {
            return range.contains(VersionNumber.asVersion(versionString));
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static String jvmVersionString(int majorVersion) {
        return majorVersion >= 9 ? String.valueOf(majorVersion) : "1." + majorVersion;
    }

    /// Converts a game version to a plain string, returning `null` for unknown versions.
    private static @Nullable String resolveMinecraftVersion(HMCLGameInstance instance) {
        @Nullable GameVersionNumber version = instance.getVersion();
        if (version == null) {
            return null;
        }
        String versionString = version.toString();
        return GameVersionNumber.isKnown(versionString) ? versionString : null;
    }

    /// Parses a Fabric/Maven-style version range string into a [VersionRange].
    ///
    /// Supported forms: bare version, wildcard (`1.19.x`), operator (`>=`, `<=`, `>`, `<`, `=`,
    /// `~`), Maven interval (`[1.19, 1.20)`), and space-separated constraints that are
    /// intersected. Non-standard patterns degrade to `all()` so that they never produce false
    /// positives.
    private static VersionRange<VersionNumber> parseVersionRange(String rangeString) {
        String range = rangeString == null ? "" : rangeString.trim();
        if (range.isEmpty() || "*".equals(range)) {
            return VersionRange.all();
        }

        Matcher mavenMatcher = MAVEN_RANGE.matcher(range);
        if (mavenMatcher.matches()) {
            @Nullable String minimum = mavenMatcher.group("min");
            @Nullable String maximum = mavenMatcher.group("max");
            boolean minInclusive = "[".equals(mavenMatcher.group(1));
            boolean maxInclusive = "]".equals(mavenMatcher.group(4));
            return mavenRange(minimum, maximum, minInclusive, maxInclusive);
        }

        VersionRange<VersionNumber> result = VersionRange.all();
        for (String token : range.split("\\s+")) {
            result = result.intersectionWith(parseSingleConstraint(token));
        }
        return result;
    }

    /// Builds a range from the bounds of a Maven interval. Bounds may be omitted (`[1.19,)`)
    /// and exclusive bounds are approximated by the nearest comparable inclusive bound.
    private static VersionRange<VersionNumber> mavenRange(
            @Nullable String minimum,
            @Nullable String maximum,
            boolean minInclusive,
            boolean maxInclusive) {
        VersionRange<VersionNumber> result = VersionRange.all();
        if (minimum != null && !minimum.isBlank()) {
            result = result.intersectionWith(minInclusive ? VersionNumber.atLeast(minimum) : VersionNumber.atLeast(minimum));
        }
        if (maximum != null && !maximum.isBlank()) {
            result = result.intersectionWith(maxInclusive ? VersionNumber.atMost(maximum) : VersionNumber.atMost(maximum));
        }
        return result;
    }

    /// Parses a single version constraint such as `>=0.14.0` or `1.19.x`.
    private static VersionRange<VersionNumber> parseSingleConstraint(String token) {
        String value = token.trim();
        if (value.isEmpty() || "*".equals(value)) {
            return VersionRange.all();
        }

        Matcher matcher = CONSTRAINT.matcher(value);
        if (!matcher.matches()) {
            return VersionRange.all();
        }

        String operator = matcher.group(1);
        String version = matcher.group(2).trim();

        if (version.endsWith(".x") || version.endsWith(".X") || version.endsWith(".*")) {
            return parseWildcardRange(version);
        }

        return switch (operator == null ? "" : operator) {
            case ">=", "~", ">" -> VersionNumber.atLeast(version);
            case "<=", "<" -> VersionNumber.atMost(version);
            case "=" -> VersionRange.is(VersionNumber.asVersion(version));
            default -> VersionRange.is(VersionNumber.asVersion(version));
        };
    }

    /// Parses a wildcard version such as `1.19.x` into the range `[base, next minor)`.
    private static VersionRange<VersionNumber> parseWildcardRange(String version) {
        Matcher matcher = WILDCARD_VERSION.matcher(version);
        if (!matcher.matches()) {
            return VersionRange.all();
        }
        String[] parts = matcher.group("base").split("\\.");
        if (parts.length < 2) {
            return VersionRange.all();
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            @Nullable Integer patch = parts.length > 2 ? Integer.parseInt(parts[2]) : null;
            String minimum = patch != null ? major + "." + minor + "." + patch : major + "." + minor;
            String maximum = major + "." + (minor + 1);
            return VersionRange.between(
                    VersionNumber.asVersion(minimum),
                    VersionNumber.asVersion(maximum));
        } catch (NumberFormatException e) {
            return VersionRange.all();
        }
    }
}