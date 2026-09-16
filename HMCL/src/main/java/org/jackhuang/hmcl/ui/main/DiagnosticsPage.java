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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.diagnostics.ModDiagnosis;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.GraphicsCard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Diagnostic page that checks the selected instance's environment and reports
/// potential problems for the user.
@NotNullByDefault
public class DiagnosticsPage extends StackPane implements DecoratorPage {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm");

    /// Severity of a single diagnostic check.
    private enum Status {
        OK(SVG.CHECK_CIRCLE, "OK", Color.web("#43a047")),
        INFO(SVG.INFO, "INFO", Color.web("#42a5f5")),
        WARN(SVG.WARNING, "WARN", Color.web("#fb8c00")),
        ERROR(SVG.ERROR, "ERROR", Color.web("#e53935"));

        private final SVG icon;
        private final String label;
        private final Color color;

        Status(SVG icon, String label, Color color) {
            this.icon = icon;
            this.label = label;
            this.color = color;
        }
    }

    /// Diagnostic check result stored for later report generation.
    private record CheckResult(String category, Status status, String value, @Nullable String explanation) {
    }

    private final ComponentList componentList = new ComponentList();
    private final VBox resultsBox = new VBox();

    private volatile String lastReport = "";

    public DiagnosticsPage() {
        resultsBox.setFillWidth(true);

        JFXButton copyButton = FXUtils.newBorderButton(i18n("quantum.diag.report.copy"));
        copyButton.setGraphic(SVG.CONTENT_COPY.createIcon());
        copyButton.setOnAction(e -> FXUtils.copyText(lastReport));

        JFXButton reportButton = FXUtils.newBorderButton(i18n("quantum.diag.report.create"));
        reportButton.setGraphic(SVG.ARCHIVE.createIcon());
        reportButton.setOnAction(e -> onExportReport());

        HBox header = new HBox(8, new Region(), copyButton, reportButton);
        header.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(header.getChildren().get(0), Priority.ALWAYS);

        componentList.getContent().setAll(resultsBox);

        ScrollPane scrollPane = new ScrollPane(componentList);
        scrollPane.setFitToWidth(true);

        VBox root = new VBox(header, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(root);

        refresh();
    }

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("quantum.diag.title")));

    @Override
    public ReadOnlyObjectProperty<DecoratorPage.State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        new Thread(this::runChecks, "DiagnosticsPage-Worker").start();
    }

    private void runChecks() {
        var results = new ArrayList<CheckResult>();

        checkJava(results);
        checkMinecraftAndLoaders(results);
        checkRam(results);
        checkGpu(results);
        checkOpenGLAndVulkan(results);
        checkMods(results);

        String report = buildReport(results);

        Platform.runLater(() -> {
            lastReport = report;

            resultsBox.getChildren().clear();
            String lastCat = null;
            for (CheckResult r : results) {
                if (!r.category().equals(lastCat)) {
                    resultsBox.getChildren().add(ComponentList.createComponentListTitle(r.category()));
                    lastCat = r.category();
                }
                resultsBox.getChildren().add(createRow(r));
            }
        });
    }

    private void checkJava(List<CheckResult> results) {
        String cat = i18n("quantum.diag.section.java");
        @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedInstance();
        if (instance == null) {
            results.add(new CheckResult(cat, Status.WARN,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.java.no_instance")));
            return;
        }

        GameSettings.Effective settings = instance.getEffectiveSettings();
        @Nullable JavaRuntime java;
        try {
            java = settings.getJava(instance.getVersion(), instance.getManifest());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            results.add(new CheckResult(cat, Status.ERROR,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.java.lookup_failed")));
            return;
        }

        if (java == null) {
            results.add(new CheckResult(cat, Status.ERROR,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.java.not_found", Metadata.RECOMMENDED_JAVA_VERSION)));
            return;
        }

        int parsed = java.getParsedVersion();
        @Nullable GameJavaVersion manifestJava = instance.getManifest().javaVersion();
        int required = manifestJava != null ? manifestJava.majorVersion() : Metadata.MINIMUM_REQUIRED_JAVA_VERSION;

        Status status = Status.OK;
        String explanation = java.getBinary().toString();
        if (parsed < required) {
            status = Status.ERROR;
            explanation += "\n" + i18n("quantum.diag.java.too_old", Metadata.RECOMMENDED_JAVA_VERSION);
        } else if (parsed != Metadata.RECOMMENDED_JAVA_VERSION) {
            status = Status.WARN;
            explanation += "\n" + i18n("quantum.diag.java.not_recommended", Metadata.RECOMMENDED_JAVA_VERSION);
        }

        String value = String.format("%s %s", java.getVendor() != null ? java.getVendor() : "", java.getVersion()).trim();
        results.add(new CheckResult(cat, status, value, explanation));
    }

    private void checkMinecraftAndLoaders(List<CheckResult> results) {
        String cat = i18n("quantum.diag.section.minecraft");
        @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedInstance();
        if (instance == null) {
            results.add(new CheckResult(cat, Status.WARN,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.minecraft.no_instance")));
            return;
        }

        String gameVersion = I18n.getDisplayVersion(instance.getVersion());
        Set<ModLoaderType> loaders = instance.getModLoaders();

        StringBuilder value = new StringBuilder(gameVersion);
        for (ModLoaderType loader : loaders) {
            @Nullable String loaderVersion = instance.getComponentVersion(
                    GameComponentType.MOD_LOADERS.stream()
                            .filter(ct -> ct.getModLoaderType() == loader)
                            .findFirst()
                            .orElseThrow());
            value.append(" + ").append(loader.displayName());
            if (loaderVersion != null)
                value.append(" ").append(loaderVersion);
        }

        results.add(new CheckResult(cat, Status.OK,
                value.toString(), null));
    }

    private void checkRam(List<CheckResult> results) {
        String cat = i18n("quantum.diag.section.ram");
        @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedInstance();
        if (instance == null) {
            results.add(new CheckResult(cat, Status.WARN,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.ram.no_instance")));
            return;
        }

        long totalBytes = SystemInfo.getTotalMemorySize();
        int totalMiB = (int) (totalBytes / (1024 * 1024));
        int allocated = instance.getEffectiveSettings().getMaxMemory();
        boolean autoMemory = instance.getEffectiveSettings().getInheritable(GameSettings::autoMemoryProperty);
        int suggested = GameSettings.SUGGESTED_MEMORY;

        Status status = Status.OK;
        String explanation;
        if (!autoMemory && allocated < suggested) {
            status = Status.WARN;
            explanation = i18n("quantum.diag.ram.below_suggested", suggested);
        } else if ((long) allocated > (long) totalMiB * 3 / 4) {
            status = Status.WARN;
            explanation = i18n("quantum.diag.ram.too_high", totalMiB);
        } else {
            explanation = i18n("quantum.diag.ram.ok");
        }

        String value = String.format("%d MiB / %d MiB", allocated, totalMiB);
        results.add(new CheckResult(cat, status, value, explanation));
    }

    private void checkGpu(List<CheckResult> results) {
        String cat = i18n("quantum.diag.section.gpu");
        @Nullable List<GraphicsCard> cards = SystemInfo.getGraphicsCards();
        if (cards == null || cards.isEmpty()) {
            results.add(new CheckResult(cat, Status.WARN,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.gpu.not_found")));
            return;
        }

        boolean hasDiscrete = false;
        StringBuilder names = new StringBuilder();
        for (GraphicsCard card : cards) {
            if (!names.isEmpty()) names.append(", ");
            names.append(card.getName());
            if (card.getType() == GraphicsCard.Type.Discrete) hasDiscrete = true;
        }

        Status status = hasDiscrete ? Status.OK : Status.WARN;
        String explanation = hasDiscrete ? null : i18n("quantum.diag.gpu.no_discrete");
        results.add(new CheckResult(cat, status, names.toString(), explanation));
    }

    private void checkOpenGLAndVulkan(List<CheckResult> results) {
        String cat = i18n("quantum.diag.section.graphics");

        String softwareRender = System.getenv("LIBGL_ALWAYS_SOFTWARE");
        if ("1".equals(softwareRender)) {
            results.add(new CheckResult(cat, Status.WARN,
                    i18n("quantum.diag.graphics.software_render"),
                    i18n("quantum.diag.graphics.software_render.detail")));
        } else {
            results.add(new CheckResult(cat, Status.INFO,
                    i18n("quantum.diag.graphics.manual_check"),
                    null));
        }

        @Nullable List<GraphicsCard> cards = SystemInfo.getGraphicsCards();
        boolean hasVulkanDriver = cards != null && cards.stream().anyMatch(c -> !c.getVulkanDriverFiles().isEmpty());
        String vulkanValue = i18n("quantum.diag.graphics.driver_found");
        String vulkanDetail = null;
        Status vulkanStatus = Status.OK;
        if (!hasVulkanDriver) {
            vulkanStatus = Status.INFO;
            vulkanValue = i18n("quantum.diag.graphics.driver_unknown");
            vulkanDetail = i18n("quantum.diag.graphics.vulkan.detail");
        }
        results.add(new CheckResult(cat, vulkanStatus, vulkanValue, vulkanDetail));
    }

    private void checkMods(List<CheckResult> results) {
        String cat = i18n("quantum.diag.section.mods");
        @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedInstance();
        if (instance == null) {
            results.add(new CheckResult(cat, Status.WARN,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.mods.no_instance")));
            return;
        }

        try {
            List<ModDiagnosis.ModProblem> problems = ModDiagnosis.analyze(instance);
            if (problems.isEmpty()) {
                results.add(new CheckResult(cat, Status.OK,
                        i18n("quantum.diag.mods.ok"), null));
                return;
            }
            for (ModDiagnosis.ModProblem problem : problems) {
                Status status = switch (problem.level()) {
                    case OK -> Status.OK;
                    case INFO -> Status.INFO;
                    case WARNING -> Status.WARN;
                    case ERROR -> Status.ERROR;
                };
                results.add(new CheckResult(cat, status,
                        problem.message(), problem.detail()));
            }
        } catch (Exception e) {
            LOG.warning("Mod diagnosis failed", e);
            results.add(new CheckResult(cat, Status.ERROR,
                    i18n("quantum.diag.value.unknown"),
                    i18n("quantum.diag.mods.failed")));
        }
    }

    // ========================= UI Helpers =========================

    private Node createRow(CheckResult r) {
        SVGPath icon = new SVGPath();
        icon.setContent(r.status().icon.getPath());
        icon.setFill(r.status().color);

        StackPane iconPane = new StackPane(icon);
        iconPane.setPrefSize(24, 24);

        Label titleLabel = new Label(r.value());
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setWrapText(true);
        titleLabel.setMouseTransparent(true);

        Label subtitleLabel = new Label(r.explanation());
        subtitleLabel.getStyleClass().add("subtitle-label");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMouseTransparent(true);
        subtitleLabel.setVisible(r.explanation() != null && !r.explanation().isEmpty());
        subtitleLabel.setManaged(subtitleLabel.isVisible());

        VBox textBox = new VBox(2, titleLabel, subtitleLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label statusLabel = new Label(r.status().label);
        statusLabel.getStyleClass().add("subtitle-label");
        statusLabel.setStyle("-fx-text-fill: " + toCssHex(r.status().color));

        HBox row = new HBox(12, iconPane, textBox, statusLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 16, 10, 16));
        row.getStyleClass().add("options-list-item");
        return row;
    }

    private static String toCssHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    // ========================= Report =========================

    private String buildReport(List<CheckResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(Metadata.FULL_TITLE).append(" — ").append(i18n("quantum.diag.title")).append(" ===\n");
        sb.append(i18n("quantum.diag.report.launcher")).append(": ").append(Metadata.FULL_TITLE).append('\n');
        sb.append(i18n("quantum.diag.report.os")).append(": ")
                .append(OperatingSystem.CURRENT_OS.getCheckedName()).append(' ')
                .append(OperatingSystem.SYSTEM_VERSION).append('\n');

        long totalBytes = SystemInfo.getTotalMemorySize();
        sb.append(i18n("quantum.diag.report.ram")).append(": ").append(totalBytes / (1024 * 1024)).append(" MiB\n");

        @Nullable JavaRuntime currentJava = JavaRuntime.CURRENT_JAVA;
        if (currentJava != null) {
            sb.append(i18n("quantum.diag.report.java")).append(": ")
                    .append(currentJava.getVersion()).append('\n');
        }

        @Nullable List<GraphicsCard> cards = SystemInfo.getGraphicsCards();
        if (cards != null && !cards.isEmpty()) {
            sb.append(i18n("quantum.diag.report.gpu")).append(": ");
            boolean first = true;
            for (GraphicsCard card : cards) {
                if (!first) sb.append(", ");
                sb.append(card.getName());
                first = false;
            }
            sb.append('\n');
        }

        sb.append('\n');

        String lastCat = null;
        for (CheckResult r : results) {
            if (!r.category().equals(lastCat)) {
                sb.append('\n').append(r.category()).append(":\n");
                lastCat = r.category();
            }
            sb.append('[').append(r.status().label).append("] ").append(r.value());
            if (r.explanation() != null && !r.explanation().isEmpty())
                sb.append(" — ").append(r.explanation());
            sb.append('\n');
        }
        return sb.toString();
    }

    private void onExportReport() {
        try {
            var chooser = new javafx.stage.FileChooser();
            chooser.setTitle(i18n("quantum.diag.report.save"));
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Text", "*.txt"));
            chooser.setInitialFileName("quantum-diagnostic-" + LocalDateTime.now().format(DATE_FMT) + ".txt");

            @Nullable Path file = Controllers.showSaveDialog(chooser);
            if (file != null) {
                Files.writeString(file, lastReport, StandardCharsets.UTF_8);
                FXUtils.showFileInExplorer(file);
            }
        } catch (IOException e) {
            LOG.warning("Failed to save diagnostic report", e);
            Controllers.dialog(i18n("quantum.diag.report.failed") + "\n" + e.getMessage(),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
        }
    }
}
