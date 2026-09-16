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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.QuantumMeta;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Shows the bundled changelog for the current version in a dialog.
@NotNullByDefault
public final class WhatNewPage extends HBox {

    /// The packaged changelog resource, relative to the classpath root.
    private static final String CHANGELOG_RESOURCE = "/assets/changelog/CHANGELOG.md";

    /// The preferred body height of the dialog.
    private static final double BODY_HEIGHT = 400;

    private @Nullable ButtonBase cancelButton;

    /// Creates the "What's new" dialog with a title, the changelog content, and a close button.
    public WhatNewPage() {
        setSpacing(16);
        getStyleClass().add("jfx-dialog-layout");

        VBox vbox = new VBox();
        HBox.setHgrow(vbox, Priority.ALWAYS);
        {
            StackPane titlePane = new StackPane();
            titlePane.getStyleClass().addAll("jfx-layout-heading", "title");
            titlePane.getChildren().setAll(new Label(i18n("quantum.whatnew.title", Metadata.VERSION)));

            StackPane content = new StackPane();
            content.getStyleClass().add("jfx-layout-body");
            TextArea textArea = new TextArea(loadChangelog());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            ScrollPane scrollPane = new ScrollPane(textArea);
            FXUtils.smoothScrolling(scrollPane);
            scrollPane.setPrefHeight(BODY_HEIGHT);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            content.getChildren().setAll(scrollPane);

            HBox actions = new HBox();
            actions.getStyleClass().add("jfx-layout-actions");

            JFXButton closeButton = new JFXButton(i18n("quantum.whatnew.close"));
            closeButton.getStyleClass().add("dialog-accept");
            closeButton.setOnAction(e -> {
                QuantumMeta.markSeen();
                fireEvent(new DialogCloseEvent());
            });
            actions.getChildren().add(closeButton);
            cancelButton = closeButton;

            vbox.getChildren().setAll(titlePane, content, actions);
        }

        getChildren().setAll(vbox);

        onEscPressed(this, () -> {
            if (cancelButton != null) {
                cancelButton.fire();
            }
        });
    }

    /// Reads the packaged changelog as UTF-8 text.
    ///
    /// @return the changelog content, or a localized placeholder when the resource is unavailable
    private static String loadChangelog() {
        try (InputStream inputStream = WhatNewPage.class.getResourceAsStream(CHANGELOG_RESOURCE)) {
            if (inputStream == null) {
                LOG.warning("Resources not found: " + CHANGELOG_RESOURCE);
                return i18n("quantum.whatnew.not_found");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warning("Failed to load changelog: " + CHANGELOG_RESOURCE, e);
            return i18n("quantum.whatnew.load_failed");
        }
    }
}