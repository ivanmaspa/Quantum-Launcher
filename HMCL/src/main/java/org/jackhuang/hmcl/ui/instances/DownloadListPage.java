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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class DownloadListPage extends Control implements DecoratorPage {
    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final boolean instanceSelection;
    private final ObjectProperty<HMCLGameInstance.Optional> instanceReference = new SimpleObjectProperty<>();
    private final IntegerProperty pageOffset = new SimpleIntegerProperty(0);
    private final IntegerProperty pageCount = new SimpleIntegerProperty(-1);
    private final ListProperty<RemoteAddon> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final ObservableList<GameInstanceID> instances = FXCollections.observableArrayList();
    private final ObjectProperty<GameInstanceID> selectedInstance = new SimpleObjectProperty<>();
    private final DownloadPage.DownloadCallback callback;
    private boolean searchInitialized = false;
    protected final BooleanProperty supportChinese = new SimpleBooleanProperty();
    private final ObservableList<Node> actions = FXCollections.observableArrayList();
    protected final ListProperty<String> downloadSources = new SimpleListProperty<>(this, "downloadSources", FXCollections.observableArrayList());
    protected final StringProperty downloadSource = new SimpleStringProperty();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private int searchID = 0;
    protected RemoteAddonRepository repository;
    private final DownloadProvider downloadProvider;

    private final ObservableSet<String> installedKeys = FXCollections.observableSet();
    private boolean installSweepDone = false;

    /// Whether the download list uses batch selection (checkboxes + "Download selected")
    /// instead of the per-result direct download button.
    private final BooleanProperty selectionMode = new SimpleBooleanProperty();
    /// Project keys of the search results currently marked in batch selection mode.
    private final ObservableSet<String> selectedKeys = FXCollections.observableSet();

    private Runnable retrySearch;

    public DownloadListPage(RemoteAddonRepository repository) {
        this(repository, null, false);
    }

    public DownloadListPage(RemoteAddonRepository repository, DownloadPage.DownloadCallback callback, boolean instanceSelection) {
        this.repository = repository;
        this.callback = callback;
        this.instanceSelection = instanceSelection;
        this.downloadProvider = DownloadProviders.getDownloadProvider();
        this.selectionMode.bind(SettingsManager.settings().directAddonDownloadProperty().not());
    }

    public BooleanProperty selectionModeProperty() {
        return selectionMode;
    }

    public DownloadProvider getDownloadProvider() {
        return downloadProvider;
    }

    /// Directly downloads the latest version of the given addon that is compatible with the
    /// selected instance's game version and installed mod loader(s), without opening the addon page.
    ///
    /// @param addon the addon from the search results
    public void quickDownload(RemoteAddon addon) {
        HMCLGameInstance.Optional instanceReference = getInstanceOptional();
        @Nullable HMCLGameInstance instance = instanceReference.instance();
        if (instance == null) {
            Controllers.showToast(i18n("download.direct.no_instance"));
            return;
        }

        String subdirectory = switch (repository.getType()) {
            case MOD -> "mods";
            case RESOURCE_PACK -> "resourcepacks";
            case SHADER_PACK -> "shaderpacks";
            default -> null;
        };
        if (subdirectory == null) {
            return;
        }

        String gameVersion = instance.getVersion().toString();
        Set<ModLoaderType> loaders = repository.getType() == RemoteAddon.Type.MOD
                ? instance.getModLoaders()
                : Set.of();

        // Resolve the latest compatible version silently; only the actual file download shows a dialog.
        Task.supplyAsync(() -> {
            RemoteAddon.Version latest;
            try {
                latest = resolveLatestVersion(addon, gameVersion, loaders);
            } catch (IOException e) {
                LOG.warning("Failed to fetch versions of " + addon.slug() + " for direct download", e);
                latest = null;
            }
            return latest;
        }).whenComplete(Schedulers.javafx(), (latest, exception) -> {
            if (exception instanceof CancellationException) {
                return;
            }
            if (exception != null || latest == null) {
                Controllers.showToast(i18n("download.direct.no_version"));
                return;
            }

            FileDownloadTask downloadTask = createFileDownloadTask(instance, subdirectory, latest);

            Task<Void> download = Task.composeAsync(() -> downloadTask)
                    .whenComplete(Schedulers.javafx(), (result, downloadException) -> {
                        if (downloadException instanceof CancellationException) {
                            return;
                        }
                        if (downloadException != null) {
                            Controllers.dialog(DownloadProviders.localizeErrorMessage(downloadException), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                        } else {
                            installedKeys.add(latest.projectId());
                            Controllers.showToast(i18n("install.success"));
                        }
                    });
            Controllers.taskDialog(download, i18n("message.downloading"), TaskCancellationAction.NORMAL);
        }).start();
    }

    /// Returns the latest version of the given addon that is compatible with the given
    /// game version and installed mod loader(s).
    ///
    /// @param addon the addon from the search results
    /// @param gameVersion the instance's Minecraft version
    /// @param loaders the loaders to filter by, or an empty set for resource/shader packs
    /// @return the latest compatible version, or {@code null} if none matches
    private @Nullable RemoteAddon.Version resolveLatestVersion(RemoteAddon addon, String gameVersion, Set<ModLoaderType> loaders) throws IOException {
        try (Stream<RemoteAddon.Version> versions = addon.data().loadVersions(repository, downloadProvider)) {
            return versions
                    .filter(version -> version.gameVersions().isEmpty() || version.gameVersions().contains(gameVersion))
                    .filter(version -> loaders.isEmpty() || version.loaders().stream().anyMatch(loader ->
                            loader.type() instanceof ModLoaderType modLoaderType && loaders.contains(modLoaderType)))
                    .max(Comparator.comparing(RemoteAddon.Version::datePublished)
                            .thenComparing(version -> version.versionType(), Comparator.reverseOrder()))
                    .orElse(null);
        }
    }

    /// Creates a file download task that saves the given add-on version into the instance's
    /// {@code mods}/{@code resourcepacks}/{@code shaderpacks} directory.
    ///
    /// @param instance the selected instance
    /// @param subdirectory the target subdirectory of the run directory
    /// @param version the add-on version to download
    /// @return the configured file download task
    private FileDownloadTask createFileDownloadTask(HMCLGameInstance instance, String subdirectory, RemoteAddon.Version version) {
        Path dest = instance.getRunDirectory().resolve(subdirectory).resolve(version.file().filename());
        FileDownloadTask downloadTask = new FileDownloadTask(
                downloadProvider.injectURLWithCandidates(version.file().url()),
                dest,
                version.file().getIntegrityCheck());
        downloadTask.setName(version.name() + ' ' + version.version());
        return downloadTask;
    }

    /// Downloads all add-ons currently marked with a checkbox in batch selection mode.
    /// Versions are resolved silently at once, then every matching file is downloaded
    /// (shown in a single progress dialog) and marked as installed on success.
    public void downloadSelected() {
        HMCLGameInstance.Optional instanceReference = getInstanceOptional();
        @Nullable HMCLGameInstance instance = instanceReference.instance();
        if (instance == null) {
            Controllers.showToast(i18n("download.direct.no_instance"));
            return;
        }

        String subdirectory = switch (repository.getType()) {
            case MOD -> "mods";
            case RESOURCE_PACK -> "resourcepacks";
            case SHADER_PACK -> "shaderpacks";
            default -> null;
        };
        if (subdirectory == null) {
            return;
        }

        String gameVersion = instance.getVersion().toString();
        Set<ModLoaderType> loaders = repository.getType() == RemoteAddon.Type.MOD
                ? instance.getModLoaders()
                : Set.of();

        List<RemoteAddon> addons = items.stream()
                .filter(addon -> addon != null && selectedKeys.contains(projectKey(addon)))
                .collect(Collectors.toList());
        if (addons.isEmpty()) {
            return;
        }

        // Resolve all versions silently first, then download the matching files together.
        Task.supplyAsync(() -> {
            List<RemoteAddon.Version> versions = new ArrayList<>();
            for (RemoteAddon addon : addons) {
                RemoteAddon.Version latest;
                try {
                    latest = resolveLatestVersion(addon, gameVersion, loaders);
                } catch (IOException e) {
                    LOG.warning("Failed to fetch versions of " + addon.slug() + " for selected download", e);
                    continue;
                }
                if (latest != null) {
                    versions.add(latest);
                }
            }
            return versions;
        }).whenComplete(Schedulers.javafx(), (versions, exception) -> {
            if (exception instanceof CancellationException) {
                return;
            }
            if (exception != null) {
                Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                return;
            }
            if (versions.isEmpty()) {
                Controllers.showToast(i18n("download.direct.no_version"));
                return;
            }

            List<FileDownloadTask> fileTasks = versions.stream()
                    .map(version -> createFileDownloadTask(instance, subdirectory, version))
                    .collect(Collectors.toList());
            Task<?> download = Task.allOf(fileTasks);
            download.whenComplete(Schedulers.javafx(), (result, downloadException) -> {
                if (downloadException instanceof CancellationException) {
                    return;
                }
                if (downloadException != null) {
                    Controllers.dialog(DownloadProviders.localizeErrorMessage(downloadException), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                    return;
                }
                selectedKeys.removeAll(versions.stream().map(RemoteAddon.Version::projectId).collect(Collectors.toList()));
                for (RemoteAddon.Version version : versions) {
                    installedKeys.add(version.projectId());
                }
                Controllers.showToast(i18n("install.success"));
            });
            Controllers.taskDialog(download, i18n("message.downloading"), TaskCancellationAction.NORMAL);
        }).start();
    }

    /// Returns a key identifying the given addon in {@link #installedKeys}.
    /// Must equal {@link RemoteAddon.Version#projectId()} so that a downloaded or locally
    /// matched version can hide the corresponding search result's button:
    /// CurseForge uses the numeric project id, Modrinth uses the project id (not the slug).
    ///
    /// @param addon the addon from the search results
    /// @return the project key
    private static String projectKey(RemoteAddon addon) {
        if (addon.data() instanceof CurseForgeRemoteAddonRepository.CurseAddon curse) {
            return Integer.toString(curse.id());
        }
        if (addon.data() instanceof ModrinthRemoteAddonRepository.ProjectSearchResult modrinth) {
            return modrinth.projectId();
        }
        return addon.slug();
    }

    /// Scans the locally installed addons of the selected instance and marks matching search
    /// results as already installed, so their download button is hidden. Runs at most once.
    public void sweepInstalled() {
        if (installSweepDone) {
            return;
        }
        installSweepDone = true;

        HMCLGameInstance.Optional instanceReference = getInstanceOptional();
        @Nullable HMCLGameInstance instance = instanceReference.instance();
        if (instance == null) {
            return;
        }

        List<Path> localFiles;
        try {
            localFiles = switch (repository.getType()) {
                case MOD -> instance.getModManager().getLocalFiles().stream()
                        .map(org.jackhuang.hmcl.addon.LocalAddonFile::getFile)
                        .toList();
                case RESOURCE_PACK -> listAddonFiles(instance.getResourcePackDirectory());
                case SHADER_PACK -> listAddonFiles(instance.getRunDirectory().resolve("shaderpacks"));
                default -> List.of();
            };
        } catch (IOException e) {
            LOG.warning("Failed to list local addons", e);
            return;
        }

        Task.supplyAsync(() -> {
            Set<String> keys = new HashSet<>();
            for (Path file : localFiles) {
                try {
                    repository.getRemoteVersionByLocalFile(file)
                            .map(RemoteAddon.Version::projectId)
                            .ifPresent(keys::add);
                } catch (IOException e) {
                    LOG.warning("Failed to match local addon " + file + " in " + repository.getBaseUrl(), e);
                }
            }
            return keys;
        }).whenComplete(Schedulers.javafx(), (keys, exception) -> {
            if (exception == null && keys != null) {
                installedKeys.addAll(keys);
            }
        }).start();
    }

    private static List<Path> listAddonFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            LOG.warning("Failed to list addons in " + directory, e);
            return List.of();
        }
    }

    public ObservableList<Node> getActions() {
        return actions;
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.instanceReference.set(instance);

        setLoading(false);
        setFailed(false);

        if (!searchInitialized) {
            searchInitialized = true;
            search("", null, 0, "", RemoteAddonRepository.SortType.RELEVANCY);
        }

        if (instanceSelection) {
            HMCLGameRepository repository = instance.repository();
            instances.setAll(repository.getDisplayInstances()
                    .map(DefaultGameInstance::getId)
                    .toList());
            @Nullable HMCLGameInstance repositorySelection = repository.getSelectedInstance();
            selectedInstance.set(repositorySelection != null ? repositorySelection.getId() : null);
        }

        sweepInstalled();
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public void selectInstance(GameInstanceID instanceId) {
        FXUtils.runInFX(() -> selectedInstance.set(instanceId));
    }

    private void search(String userGameVersion, RemoteAddonRepository.Category category, int pageOffset, String searchFilter, RemoteAddonRepository.SortType sort) {
        retrySearch = null;
        setLoading(true);
        setFailed(false);

        int currentSearchID = searchID = searchID + 1;
        Task.supplyAsync(() -> {
            HMCLGameInstance.Optional instanceReference = this.instanceReference.get();
            @Nullable HMCLGameInstance instance = instanceReference.instance();
            if (instance == null) {
                return userGameVersion;
            } else {
                GameVersionNumber version = instance.getVersion();
                return version != GameVersionNumber.unknown() ? version.toString() : "";
            }
        }).thenApplyAsync(
                gameVersion -> repository.search(downloadProvider, gameVersion, category, pageOffset, 50, searchFilter, sort, RemoteAddonRepository.SortOrder.DESC)
        ).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (searchID != currentSearchID) {
                return;
            }

            setLoading(false);
            if (exception == null) {
                items.setAll(result.results().collect(Collectors.toList()));
                pageCount.set(result.totalPages());
                failed.set(false);
            } else {
                failed.set(true);
                pageCount.set(-1);
                retrySearch = () -> search(userGameVersion, category, pageOffset, searchFilter, sort);
            }
        }).executor(true);
    }

    protected String getLocalizedCategory(String category, Object self) {
        return repository instanceof ModrinthRemoteAddonRepository
                ? i18n("modrinth.category." + category)
                : i18n("curse.category." + category);
    }

    protected boolean shouldDisplayCategory(String category) {
        return !"minecraft".equals(category);
    }

    private String getLocalizedCategoryIndent(ModDownloadListPageSkin.CategoryIndented category) {
        return StringUtils.repeats(' ', category.indent * 4) +
                (category.category() == null
                        ? i18n("curse.category.0")
                        : getLocalizedCategory(category.category().id(), category.category().self()));
    }

    protected String getLocalizedOfficialPage() {
        if (repository instanceof ModrinthRemoteAddonRepository) {
            return i18n("addon.modrinth");
        } else {
            return i18n("addon.curseforge");
        }
    }

    protected HMCLGameInstance.Optional getInstanceOptional() {
        if (instanceSelection) {
            @Nullable GameInstanceID instanceId = selectedInstance.get();
            return HMCLGameInstance.Optional.of(instanceReference.get().repository(), instanceId);
        } else {
            return instanceReference.get();
        }
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadListPageSkin(this);
    }

    private static class ModDownloadListPageSkin extends SkinBase<DownloadListPage> {
        private final JFXListView<RemoteAddon> listView = new JFXListView<>();
        private final RemoteImageLoader iconLoader;

        protected ModDownloadListPageSkin(DownloadListPage control) {
            super(control);

            listView.getStyleClass().add("no-horizontal-scrollbar");

            iconLoader = new RemoteImageLoader(control.downloadProvider) {
                @Override
                protected @NotNull Task<Image> createLoadTask(@NotNull List<URI> uris) {
                    return FXUtils.getRemoteImageTask(uris, 80, 80, true, true);
                }
            };

            BorderPane pane = new BorderPane();

            GridPane searchPane = new GridPane();
            pane.setTop(searchPane);
            searchPane.getStyleClass().addAll("card");
            BorderPane.setMargin(searchPane, new Insets(10, 10, 0, 10));

            ColumnConstraints nameColumn = new ColumnConstraints();
            nameColumn.setMinWidth(USE_PREF_SIZE);
            ColumnConstraints column1 = new ColumnConstraints();
            column1.setHgrow(Priority.ALWAYS);
            ColumnConstraints column2 = new ColumnConstraints();
            column2.setHgrow(Priority.ALWAYS);
            searchPane.getColumnConstraints().setAll(nameColumn, column1, nameColumn, column2);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                int rowIndex = 0;

                if (control.instanceSelection || !control.downloadSources.isEmpty()) {
                    searchPane.addRow(rowIndex);
                    int columns = 0;
                    Node lastNode = null;
                    if (control.instanceSelection) {
                        JFXComboBox<GameInstanceID> instancesComboBox = new JFXComboBox<>();
                        instancesComboBox.setMaxWidth(Double.MAX_VALUE);
                        Bindings.bindContent(instancesComboBox.getItems(), control.instances);
                        selectedItemPropertyFor(instancesComboBox).bindBidirectional(control.selectedInstance);

                        searchPane.add(new Label(i18n("instance")), columns++, rowIndex);
                        searchPane.add(lastNode = instancesComboBox, columns++, rowIndex);
                    }

                    if (control.downloadSources.getSize() > 1) {
                        JFXComboBox<String> downloadSourceComboBox = new JFXComboBox<>();
                        downloadSourceComboBox.setMaxWidth(Double.MAX_VALUE);
                        downloadSourceComboBox.getItems().setAll(control.downloadSources.get());
                        downloadSourceComboBox.setConverter(stringConverter(I18n::i18n));
                        selectedItemPropertyFor(downloadSourceComboBox).bindBidirectional(control.downloadSource);

                        searchPane.add(new Label(i18n("settings.launcher.download_source")), columns++, rowIndex);
                        searchPane.add(lastNode = downloadSourceComboBox, columns++, rowIndex);
                    }

                    if (columns == 2) {
                        GridPane.setColumnSpan(lastNode, 3);
                    }

                    rowIndex++;
                }

                JFXTextField nameField = new JFXTextField();
                nameField.setPromptText(getSkinnable().supportChinese.get() ? i18n("search.hint.chinese") : i18n("search.hint.english"));
                if (getSkinnable().supportChinese.get()) {
                    FXUtils.installFastTooltip(nameField, i18n("search.hint.chinese"));
                } else {
                    FXUtils.installFastTooltip(nameField, i18n("search.hint.english"));
                }

                JFXComboBox<String> gameVersionField = new JFXComboBox<>();
                gameVersionField.setMaxWidth(Double.MAX_VALUE);
                gameVersionField.setEditable(true);
                gameVersionField.getItems().setAll(GameVersionNumber.getDefaultGameVersions());
                Label lblGameVersion = new Label(i18n("world.game_version"));
                searchPane.addRow(rowIndex++, new Label(i18n("mods.name")), nameField, lblGameVersion, gameVersionField);

                ObjectBinding<Boolean> hasVersion = BindingMapping.of(getSkinnable().instanceReference)
                        .map(instanceReference -> instanceReference.instanceId() == null);
                lblGameVersion.managedProperty().bind(hasVersion);
                lblGameVersion.visibleProperty().bind(hasVersion);
                gameVersionField.managedProperty().bind(hasVersion);
                gameVersionField.visibleProperty().bind(hasVersion);
                FXUtils.installFastTooltip(gameVersionField, i18n("search.enter"));

                FXUtils.onChangeAndOperate(getSkinnable().instanceReference, instanceReference -> {
                    if (instanceReference.instanceId() != null) {
                        GridPane.setColumnSpan(nameField, 3);
                    } else {
                        GridPane.setColumnSpan(nameField, 1);
                    }
                });

                StackPane categoryStackPane = new StackPane();
                JFXComboBox<CategoryIndented> categoryComboBox = new JFXComboBox<>();
                categoryComboBox.getItems().setAll(CategoryIndented.ALL);
                categoryStackPane.getChildren().setAll(categoryComboBox);
                categoryComboBox.prefWidthProperty().bind(categoryStackPane.widthProperty());
                categoryComboBox.getStyleClass().add("fit-width");
                categoryComboBox.setPromptText(i18n("addon.category"));
                categoryComboBox.getSelectionModel().select(0);
                categoryComboBox.setConverter(stringConverter(getSkinnable()::getLocalizedCategoryIndent));
                FXUtils.onChangeAndOperate(getSkinnable().downloadSource, downloadSource -> {
                    categoryComboBox.getItems().setAll(CategoryIndented.ALL);
                    categoryComboBox.getSelectionModel().select(0);

                    Task.supplyAsync(() -> getSkinnable().repository.getCategories())
                            .thenAcceptAsync(Schedulers.javafx(), categories -> {
                                if (!Objects.equals(getSkinnable().downloadSource.get(), downloadSource)) {
                                    return;
                                }

                                List<CategoryIndented> result = new ArrayList<>();
                                result.add(CategoryIndented.ALL);
                                for (RemoteAddonRepository.Category category : Lang.toIterable(categories)) {
                                    resolveCategory(category, 0, result);
                                }
                                categoryComboBox.getItems().setAll(result);
                                categoryComboBox.getSelectionModel().select(0);
                            }).start();
                });

                StackPane sortStackPane = new StackPane();
                JFXComboBox<RemoteAddonRepository.SortType> sortComboBox = new JFXComboBox<>();
                sortStackPane.getChildren().setAll(sortComboBox);
                sortComboBox.prefWidthProperty().bind(sortStackPane.widthProperty());
                sortComboBox.getStyleClass().add("fit-width");
                sortComboBox.setConverter(stringConverter(sortType -> i18n("addon.sort." + sortType.name().toLowerCase(Locale.ROOT))));
                sortComboBox.getItems().setAll(RemoteAddonRepository.SortType.values());
                sortComboBox.getSelectionModel().select(0);
                searchPane.addRow(rowIndex++, new Label(i18n("addon.category")), categoryStackPane, new Label(i18n("search.sort")), sortStackPane);

                IntegerProperty filterID = new SimpleIntegerProperty(this, "Filter ID", 0);
                IntegerProperty currentFilterID = new SimpleIntegerProperty(this, "Current Filter ID", -1);
                EventHandler<ActionEvent> searchAction = e -> {
                    iconLoader.clearInvalidCache();
                    if (currentFilterID.get() != -1 && currentFilterID.get() != filterID.get()) {
                        control.pageOffset.set(0);
                    }
                    currentFilterID.set(filterID.get());

                    int pageOffset = control.pageOffset.get();
                    getSkinnable().search(gameVersionField.getSelectionModel().getSelectedItem(),
                            Optional.ofNullable(categoryComboBox.getSelectionModel().getSelectedItem())
                                    .map(CategoryIndented::category)
                                    .orElse(null),
                            pageOffset == -1 ? 0 : pageOffset,
                            nameField.getText(),
                            sortComboBox.getSelectionModel().getSelectedItem());
                };

                control.listenerHolder.add(FXUtils.observeWeak(
                        () -> filterID.set(filterID.get() + 1),

                        control.downloadSource,
                        gameVersionField.getSelectionModel().selectedItemProperty(),
                        categoryComboBox.getSelectionModel().selectedItemProperty(),
                        nameField.textProperty(),
                        sortComboBox.getSelectionModel().selectedItemProperty()
                ));

                HBox actionsBox = new HBox(8);
                GridPane.setColumnSpan(actionsBox, 4);
                actionsBox.setAlignment(Pos.CENTER);
                {
                    AggregatedObservableList<Node> actions = new AggregatedObservableList<>();

                    Holder<Runnable> changeButton = new Holder<>();

                    JFXButton firstPageButton = FXUtils.newBorderButton(i18n("search.first_page"));
                    firstPageButton.setOnAction(event -> {
                        control.pageOffset.set(0);
                        searchAction.handle(event);
                        changeButton.value.run();
                    });

                    JFXButton previousPageButton = FXUtils.newBorderButton(i18n("search.previous_page"));
                    previousPageButton.setOnAction(event -> {
                        int pageOffset = control.pageOffset.get();
                        if (pageOffset > 0) {
                            control.pageOffset.set(pageOffset - 1);
                            searchAction.handle(event);
                            changeButton.value.run();
                        }
                    });

                    Label pageDescription = new Label();
                    pageDescription.textProperty().bind(Bindings.createStringBinding(() -> {
                        int pageCount = control.pageCount.get();
                        return i18n("search.page_n", control.pageOffset.get() + 1, pageCount == -1 ? "-" : String.valueOf(pageCount));
                    }, control.pageOffset, control.pageCount));

                    JFXButton nextPageButton = FXUtils.newBorderButton(i18n("search.next_page"));
                    nextPageButton.setOnAction(event -> {
                        int nv = control.pageOffset.get() + 1;
                        if (nv < control.pageCount.get()) {
                            control.pageOffset.set(nv);
                            searchAction.handle(event);
                            changeButton.value.run();
                        }
                    });

                    JFXButton lastPageButton = FXUtils.newBorderButton(i18n("search.last_page"));
                    lastPageButton.setOnAction(event -> {
                        control.pageOffset.set(control.pageCount.get() - 1);
                        searchAction.handle(event);
                        changeButton.value.run();
                    });

                    firstPageButton.setDisable(true);
                    previousPageButton.setDisable(true);
                    lastPageButton.setDisable(true);
                    nextPageButton.setDisable(true);

                    changeButton.value = () -> {
                        int pageOffset = control.pageOffset.get();
                        int pageCount = control.pageCount.get();

                        boolean disableAll = pageCount >= -1 && pageCount <= 1;

                        boolean disablePrevious = disableAll || pageOffset == 0;
                        firstPageButton.setDisable(disablePrevious);
                        previousPageButton.setDisable(disablePrevious);

                        boolean disableNext = disableAll || pageOffset == pageCount - 1;
                        nextPageButton.setDisable(disableNext);
                        lastPageButton.setDisable(disableNext);

                        listView.scrollTo(0);
                    };

                    FXUtils.onChange(control.pageCount, pageCountN -> {
                        int pageCount = pageCountN.intValue();

                        if (pageCount != -1) {
                            if (control.pageOffset.get() + 1 >= pageCount) {
                                control.pageOffset.set(pageCount - 1);
                            }
                        }

                        changeButton.value.run();
                    });

                    FXUtils.onChange(control.pageOffset, pageOffsetN -> {
                        changeButton.value.run();
                    });

                    Pane placeholder = new Pane();
                    HBox.setHgrow(placeholder, Priority.SOMETIMES);

                    JFXButton searchButton = FXUtils.newRaisedButton(i18n("search"));
                    searchButton.setOnAction(searchAction);

                    JFXButton modeToggleButton = FXUtils.newBorderButton(null);
                    modeToggleButton.textProperty().bind(Bindings.createStringBinding(() ->
                                    i18n(getSkinnable().selectionModeProperty().get()
                                            ? "download.direct.mode.direct"
                                            : "download.direct.mode.selected"),
                            getSkinnable().selectionModeProperty()));
                    modeToggleButton.setOnAction(e -> SettingsManager.settings().directAddonDownloadProperty()
                            .set(!SettingsManager.settings().directAddonDownloadProperty().get()));

                    JFXButton batchDownloadButton = FXUtils.newRaisedButton(i18n("download.direct.selected"));
                    batchDownloadButton.disableProperty().bind(Bindings.createBooleanBinding(
                            () -> getSkinnable().selectedKeys.isEmpty(),
                            getSkinnable().selectedKeys));
                    batchDownloadButton.visibleProperty().bind(getSkinnable().selectionModeProperty());
                    batchDownloadButton.managedProperty().bind(getSkinnable().selectionModeProperty());
                    batchDownloadButton.setOnAction(e -> getSkinnable().downloadSelected());

                    actions.appendList(FXCollections.observableArrayList(firstPageButton, previousPageButton, pageDescription, nextPageButton, lastPageButton, placeholder, modeToggleButton, batchDownloadButton, searchButton));
                    actions.appendList(control.actions);
                    Bindings.bindContent(actionsBox.getChildren(), actions.getAggregatedList());
                }

                searchPane.addRow(rowIndex++, actionsBox);

                FXUtils.onChange(control.downloadSource, v -> searchAction.handle(null));
                nameField.setOnAction(searchAction);
                gameVersionField.setOnAction(searchAction);
                categoryComboBox.setOnAction(searchAction);
                sortComboBox.setOnAction(searchAction);
            }

            SpinnerPane spinnerPane = new SpinnerPane();
            pane.setCenter(spinnerPane);
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(
                    Bindings.createStringBinding(() -> {
                        if (getSkinnable().isFailed()) {
                            return i18n("download.failed.refresh");
                        } else if (!getSkinnable().isLoading() && getSkinnable().pageCount.get() >= 0 && getSkinnable().items.isEmpty()) {
                            return i18n("search.no_results_found");
                        } else {
                            return null;
                        }
                    },
                    getSkinnable().failedProperty(),
                    getSkinnable().loadingProperty(),
                    getSkinnable().pageCount,
                    getSkinnable().items)
                );
                spinnerPane.setOnFailedAction(e -> {
                    if (getSkinnable().isFailed() && getSkinnable().retrySearch != null) {
                        getSkinnable().retrySearch.run();
                    }
                });

                spinnerPane.setContent(listView);
                Bindings.bindContent(listView.getItems(), getSkinnable().items);
                listView.setSelectionModel(new NoneMultipleSelectionModel<>());
                // ListViewBehavior would consume ESC pressed event, preventing us from handling it, so we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
                listView.setCellFactory(x -> new ListCell<>() {
                    private static final Insets PADDING = new Insets(9, 9, 0, 9);
                    private static final Insets LAST_PADDING = new Insets(9, 9, 9, 9);

                    private final RipplerContainer graphic;
                    private final StackPane wrapper = new StackPane();

                    private final TwoLineListItem content = new TwoLineListItem();
                    private final ImageContainer imageContainer = new ImageContainer(40);
                    private final JFXCheckBox selectionCheckBox = new JFXCheckBox();

                    {
                        setPadding(PADDING);

                        HBox container = new HBox(8);
                        container.setPadding(new Insets(8));
                        container.setCursor(Cursor.HAND);
                        container.setAlignment(Pos.CENTER_LEFT);

                        imageContainer.setMouseTransparent(true);

                        JFXButton downloadButton = FXUtils.newToggleButton4(SVG.DOWNLOAD);
                        downloadButton.setOnAction(e -> {
                            RemoteAddon item = getItem();
                            if (item != null)
                                getSkinnable().quickDownload(item);
                        });
                        FXUtils.installFastTooltip(downloadButton, i18n("download.direct"));
                        downloadButton.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());

                        JFXCheckBox selectionCheckBox = this.selectionCheckBox;
                        selectionCheckBox.getStyleClass().add("fit-width");
                        FXUtils.installFastTooltip(selectionCheckBox, i18n("download.direct.select"));
                        selectionCheckBox.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> e.consume());
                        selectionCheckBox.setCursor(Cursor.HAND);

                        selectionCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                            RemoteAddon item = getItem();
                            if (item != null) {
                                String key = projectKey(item);
                                if (newValue) {
                                    getSkinnable().selectedKeys.add(key);
                                } else {
                                    getSkinnable().selectedKeys.remove(key);
                                }
                            }
                        });
                        getSkinnable().selectedKeys.addListener((SetChangeListener<String>) change -> {
                            RemoteAddon item = getItem();
                            if (item != null) {
                                String key = projectKey(item);
                                if (key.equals(change.getElementAdded()) || key.equals(change.getElementRemoved())) {
                                    selectionCheckBox.selectedProperty().set(getSkinnable().selectedKeys.contains(key));
                                }
                            }
                        });

                        boolean quickDownloadable = switch (getSkinnable().repository.getType()) {
                            case MOD, RESOURCE_PACK, SHADER_PACK -> true;
                            default -> false;
                        };
                        BooleanBinding installedOrAbsent = Bindings.createBooleanBinding(
                                () -> !quickDownloadable || getItem() == null
                                        || getSkinnable().installedKeys.contains(projectKey(getItem())),
                                getSkinnable().installedKeys, itemProperty());
                        downloadButton.visibleProperty().bind(Bindings.createBooleanBinding(
                                () -> !getSkinnable().selectionModeProperty().get() && !installedOrAbsent.get(),
                                getSkinnable().selectionModeProperty(), installedOrAbsent));
                        selectionCheckBox.visibleProperty().bind(Bindings.createBooleanBinding(
                                () -> getSkinnable().selectionModeProperty().get() && !installedOrAbsent.get(),
                                getSkinnable().selectionModeProperty(), installedOrAbsent));
                        selectionCheckBox.managedProperty().bind(selectionCheckBox.visibleProperty());

                        container.getChildren().setAll(imageContainer, content, selectionCheckBox, downloadButton);
                        HBox.setHgrow(content, Priority.ALWAYS);

                        this.graphic = new RipplerContainer(container);
                        wrapper.getChildren().setAll(this.graphic);
                        wrapper.getStyleClass().add("card-no-padding");

                        FXUtils.onClicked(wrapper, () -> {
                            RemoteAddon item = getItem();
                            if (item != null)
                                Controllers.navigate(new DownloadPage(getSkinnable(), item, getSkinnable().getInstanceOptional(), getSkinnable().callback));
                        });

                        setPrefWidth(0);

                        FXUtils.limitCellWidth(listView, this);

                    }

                    @Override
                    protected void updateItem(RemoteAddon item, boolean empty) {
                        RemoteAddon oldItem = getItem();
                        boolean oldEmpty = isEmpty();

                        super.updateItem(item, empty);

                        if (oldItem == item && oldEmpty == empty) return;

                        this.graphic.releaseRippleImmediately();

                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            setPadding(
                                    getIndex() == getListView().getItems().size() - 1
                                            ? LAST_PADDING
                                            : PADDING
                            );

                            selectionCheckBox.selectedProperty().set(getSkinnable().selectedKeys.contains(projectKey(item)));

                            ModTranslations.Mod mod = ModTranslations.getTranslationsByAddonType(getSkinnable().repository.getType()).getModByCurseForgeId(item.slug());
                            content.setTitle(mod != null && I18n.isUseChinese() ? mod.getDisplayName() : item.title());
                            String description = item.description();
                            if (description != null) {
                                description = description.replaceAll("\\R", " ");
                            }
                            content.setSubtitle(description);
                            content.getTags().clear();
                            for (String category : item.categories()) {
                                if (getSkinnable().shouldDisplayCategory(category))
                                    content.addTag(getSkinnable().getLocalizedCategory(category, null));
                            }
                            iconLoader.load(imageContainer.imageProperty(), item.iconUrl());
                            setGraphic(wrapper);
                        }
                    }
                });
            }

            getChildren().setAll(pane);
        }

        private record CategoryIndented(int indent, RemoteAddonRepository.Category category) {
            private static final CategoryIndented ALL = new CategoryIndented(0, null);
        }

        private static void resolveCategory(RemoteAddonRepository.Category category, int indent, List<CategoryIndented> result) {
            result.add(new CategoryIndented(indent, category));
            for (RemoteAddonRepository.Category subcategory : category.subcategories()) {
                resolveCategory(subcategory, indent + 1, result);
            }
        }
    }
}
