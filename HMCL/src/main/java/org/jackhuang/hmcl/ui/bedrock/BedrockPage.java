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
package org.jackhuang.hmcl.ui.bedrock;

import com.jfoenix.controls.JFXButton;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.bedrock.BedrockManager;
import org.jackhuang.hmcl.bedrock.BedrockVersion;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Manages Bedrock Edition versions through the [mcpelauncher](https://minecraft-linux.github.io) runtime:
///
///  * shows the runtime installation status,
///  * lists locally installed versions (play / delete),
///  * lists the versions published in the mcpelauncher version database,
///  * delegates downloading to the `mcpelauncher-ui-qt` version manager.
@NotNullByDefault
public final class BedrockPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("bedrock")));

    private final AdvancedListBox box = new AdvancedListBox();
    private final JFXButton refreshButton;

    /// The installed version names shown by the last render, compared against on each scan.
    private @Nullable List<String> lastInstalledVersions;

    /// Periodically re-scans the installed versions while the page is shown, so a version freshly
    /// downloaded by the mcpelauncher manager appears automatically.
    private final Timeline installedScanner = new Timeline(
            new KeyFrame(Duration.seconds(2), e -> scanInstalledVersions()));

    private @Nullable List<BedrockVersion> onlineVersions;
    private @Nullable Throwable onlineError;
    private boolean loadingOnline = false;

    public BedrockPage() {
        installedScanner.setCycleCount(Animation.INDEFINITE);

        box.setFitToWidth(true);
        box.setSpacing(1);

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        BorderPane header = new BorderPane();
        header.getStyleClass().add("card");
        header.setPadding(new Insets(16, 20, 16, 20));
        {
            VBox texts = new VBox(2);
            Label title = new Label(i18n("bedrock"));
            title.setStyle("-fx-font-size: 18px;");
            Label subtitle = new Label(i18n("bedrock.subtitle"));
            texts.getChildren().setAll(title, subtitle);

            refreshButton = FXUtils.newRaisedButton(i18n("bedrock.update_list"));
            refreshButton.setOnAction(e -> refreshVersionList());
            JFXButton managerButton = FXUtils.newRaisedButton(i18n("bedrock.open_manager"));
            managerButton.setOnAction(e -> openManager());

            HBox buttons = new HBox(8, refreshButton, managerButton);
            buttons.setAlignment(Pos.CENTER_RIGHT);

            header.setLeft(texts);
            header.setRight(buttons);
        }

        content.getChildren().add(header);
        VBox.setVgrow(box, Priority.ALWAYS);
        content.getChildren().add(box);

        setCenter(content);

        refreshVersionList();
    }

    /// Reloads the online version list from the mcpelauncher version database.
    private void refreshVersionList() {
        if (loadingOnline) {
            return;
        }
        loadingOnline = true;
        onlineError = null;
        refreshButton.setDisable(true);
        render();
        BedrockManager.refreshOnlineVersionsAsync()
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    loadingOnline = false;
                    refreshButton.setDisable(false);
                    if (exception != null) {
                        LOG.warning("Failed to load the Bedrock version list", exception);
                        onlineError = exception;
                    } else {
                        onlineVersions = result;
                    }
                    render();
                })
                .start();
    }

    /// Rebuilds the whole page content from the current manager state.
    private void render() {
        box.clear();

        if (OperatingSystem.CURRENT_OS != OperatingSystem.LINUX) {
            box.startCategory(i18n("bedrock.category.status").toUpperCase(Locale.ROOT));
            AdvancedListItem unsupportedItem = new AdvancedListItem();
            unsupportedItem.setLeftIcon(SVG.GAMEPAD);
            unsupportedItem.setTitle(i18n("bedrock.unsupported_os"));
            unsupportedItem.setSubtitle(i18n("bedrock.unsupported_os.tip"));
            box.add(unsupportedItem);
            return;
        }

        @Nullable Path client = BedrockManager.getClientPath();

        box.startCategory(i18n("bedrock.category.status").toUpperCase(Locale.ROOT));
        AdvancedListItem statusItem = new AdvancedListItem();
        statusItem.setLeftIcon(SVG.GAMEPAD);
        if (client != null) {
            statusItem.setTitle(i18n("bedrock.runtime.installed"));
            statusItem.setSubtitle(client.toString());
        } else {
            statusItem.setTitle(i18n("bedrock.runtime.missing"));
            statusItem.setSubtitle(BedrockManager.getRuntimeInstallCommand() != null
                    ? i18n("bedrock.runtime.missing.tip")
                    : i18n("bedrock.runtime.missing.no_manager"));
            statusItem.setRightAction(SVG.DOWNLOAD, this::installRuntime);
        }
        box.add(statusItem);

        List<String> installed = BedrockManager.getInstalledVersionNames();
        lastInstalledVersions = installed;
        box.startCategory(i18n("bedrock.category.installed").toUpperCase(Locale.ROOT));
        if (installed.isEmpty()) {
            AdvancedListItem emptyItem = new AdvancedListItem();
            emptyItem.setTitle(i18n("bedrock.installed.empty"));
            emptyItem.setSubtitle(i18n("bedrock.installed.empty.tip"));
            box.add(emptyItem);
        } else {
            for (String version : installed) {
                AdvancedListItem item = new AdvancedListItem();
                item.setLeftIcon(SVG.TEXTURE);
                item.setTitle(version);
                item.setSubtitle(BedrockManager.getVersionDirectory(version).toString());
                if (client != null) {
                    item.setRightGraphic(buildInstalledActions(version));
                }
                box.add(item);
            }
        }

        box.startCategory(i18n("bedrock.category.online").toUpperCase(Locale.ROOT));
        if (loadingOnline) {
            AdvancedListItem loadingItem = new AdvancedListItem();
            loadingItem.setTitle(i18n("bedrock.online.loading"));
            box.add(loadingItem);
        } else if (onlineError != null) {
            AdvancedListItem failedItem = new AdvancedListItem();
            failedItem.setTitle(i18n("bedrock.online.failed"));
            failedItem.setSubtitle(i18n("bedrock.online.failed.tip"));
            failedItem.setRightAction(SVG.REFRESH, this::refreshVersionList);
            box.add(failedItem);
        } else if (onlineVersions == null || onlineVersions.isEmpty()) {
            AdvancedListItem emptyItem = new AdvancedListItem();
            emptyItem.setTitle(i18n("bedrock.online.empty"));
            box.add(emptyItem);
        } else {
            for (BedrockVersion version : onlineVersions) {
                AdvancedListItem item = new AdvancedListItem();
                item.setLeftIcon(version.beta() ? SVG.EXTENSION : SVG.TEXTURE);
                item.setTitle(version.versionName());
                boolean installedOnline = BedrockManager.isVersionInstalled(version.versionName());
                item.setSubtitle(installedOnline
                        ? i18n("bedrock.online.installed")
                        : (version.beta()
                                ? i18n("bedrock.beta") + " · " + i18n("bedrock.online.download_hint")
                                : i18n("bedrock.online.download_hint")));
                if (installedOnline) {
                    if (client != null) {
                        item.setRightGraphic(buildPlayButton(version.versionName()));
                    }
                } else {
                    item.setRightGraphic(buildDownloadButton(version));
                }
                box.add(item);
            }
        }
    }

    /// Compares the locally installed versions with the last rendered snapshot and re-renders the
    /// page when a freshly downloaded game shows up.
    private void scanInstalledVersions() {
        List<String> installed = BedrockManager.getInstalledVersionNames();
        if (!installed.equals(lastInstalledVersions)) {
            lastInstalledVersions = installed;
            render();
        }
    }

    /// Re-scans the installed versions when the page is shown and starts the periodic scanner.
    @Override
    public void onPageShown() {
        render();
        installedScanner.play();
    }

    /// Stops the periodic installed-versions scanner when the page is hidden.
    @Override
    public void onPageHidden() {
        installedScanner.stop();
    }

    /// Builds the play and delete buttons for an installed version row.
    ///
    /// @param version the installed version name
    /// @return the action buttons
    private Node buildInstalledActions(String version) {
        JFXButton playButton = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH, 16);
        FXUtils.installFastTooltip(playButton, i18n("bedrock.launch"));
        playButton.setOnAction(e -> launchVersion(version));

        JFXButton deleteButton = FXUtils.newToggleButton4(SVG.DELETE, 16);
        FXUtils.installFastTooltip(deleteButton, i18n("bedrock.delete"));
        deleteButton.setOnAction(e -> confirmDeleteVersion(version));

        HBox actions = new HBox(8, playButton, deleteButton);
        actions.setAlignment(Pos.CENTER);
        return actions;
    }

    /// Builds the play button shown on rows of already installed versions.
    ///
    /// @param version the installed version name
    /// @return the play button
    private Node buildPlayButton(String version) {
        JFXButton playButton = FXUtils.newToggleButton4(SVG.ROCKET_LAUNCH, 14);
        FXUtils.installFastTooltip(playButton, i18n("bedrock.launch"));
        playButton.setOnAction(e -> launchVersion(version));

        HBox actions = new HBox(8, playButton);
        actions.setAlignment(Pos.CENTER);
        return actions;
    }

    /// Builds the download button shown on rows of not yet installed online versions.
    ///
    /// @param version the online version to download
    /// @return the download button
    private Node buildDownloadButton(BedrockVersion version) {
        JFXButton downloadButton = FXUtils.newToggleButton4(SVG.DOWNLOAD, 14);
        FXUtils.installFastTooltip(downloadButton, i18n("bedrock.download"));
        downloadButton.setOnAction(e -> downloadVersion(version));

        HBox actions = new HBox(8, downloadButton);
        actions.setAlignment(Pos.CENTER);
        return actions;
    }

    /// Handles the download action of an online version row.
    ///
    /// Bedrock Edition is distributed through Google Play and cannot be downloaded directly, so the
    /// download is delegated to the `mcpelauncher-ui-qt` manager. When the manager is not installed,
    /// the runtime install command is shown instead.
    ///
    /// @param version the online version to download
    private void downloadVersion(BedrockVersion version) {
        if (BedrockManager.isUIManagerInstalled()) {
            openManager();
            Controllers.dialog(i18n("bedrock.download.hint", version.versionName(), version.versionName()),
                    i18n("bedrock.download.title"), MessageDialogPane.MessageType.INFO);
            return;
        }
        @Nullable String command = BedrockManager.getRuntimeInstallCommand();
        if (command == null) {
            Controllers.dialog(i18n("bedrock.runtime.missing.no_manager"),
                    i18n("bedrock.download.title"), MessageDialogPane.MessageType.WARNING);
            return;
        }
        Controllers.dialog(i18n("bedrock.download.install_message", command),
                i18n("bedrock.download.title"), MessageDialogPane.MessageType.INFO);
    }

    /// Launches an installed Bedrock version on the IO scheduler and reports the result.
    ///
    /// @param version the installed version name
    private void launchVersion(String version) {
        Task.supplyAsync(Schedulers.io(), () -> {
            BedrockManager.launchVersion(version);
            return null;
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to launch Bedrock version " + version, exception);
                Controllers.dialog(i18n("bedrock.launch.failed", StringUtils.getStackTrace(exception)),
                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            } else {
                Controllers.showToast(i18n("bedrock.launch.success"));
            }
        }).start();
    }

    /// Asks for confirmation before deleting an installed version.
    ///
    /// @param version the installed version name
    private void confirmDeleteVersion(String version) {
        Controllers.confirm(i18n("bedrock.delete.message", version), i18n("bedrock.delete.title"),
                () -> deleteVersion(version), null);
    }

    /// Deletes an installed Bedrock version and re-renders the page.
    ///
    /// @param version the installed version name
    private void deleteVersion(String version) {
        Task.supplyAsync(Schedulers.io(), () -> {
            BedrockManager.deleteVersion(version);
            return null;
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to delete Bedrock version " + version, exception);
                Controllers.dialog(i18n("bedrock.delete.failed", StringUtils.getStackTrace(exception)),
                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            } else {
                Controllers.showToast(i18n("bedrock.delete.success"));
            }
            render();
        }).start();
    }

    /// Opens the `mcpelauncher-ui-qt` version manager, which handles Google Play sign-in and downloads.
    private void openManager() {
        Task.supplyAsync(Schedulers.io(), () -> {
            BedrockManager.openUIManager();
            return null;
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to open the mcpelauncher version manager", exception);
                Controllers.dialog(i18n("bedrock.open_manager.failed", StringUtils.getStackTrace(exception)),
                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }).start();
    }

    /// Shows the command that installs the mcpelauncher runtime on the current system.
    private void installRuntime() {
        @Nullable String command = BedrockManager.getRuntimeInstallCommand();
        if (command == null) {
            Controllers.dialog(i18n("bedrock.runtime.missing.no_manager"),
                    i18n("bedrock.runtime.install.title"), MessageDialogPane.MessageType.WARNING);
            return;
        }
        Controllers.dialog(i18n("bedrock.runtime.install.message", command),
                i18n("bedrock.runtime.install.title"), MessageDialogPane.MessageType.INFO);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}