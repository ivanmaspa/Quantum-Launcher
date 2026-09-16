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
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.LaunchHistory;
import org.jackhuang.hmcl.setting.LaunchHistoryManager;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineTextPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.time.Duration;
import java.time.Instant;

import static org.jackhuang.hmcl.util.i18n.I18n.formatDateTime;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Launch history page for a single game instance.
///
/// Displays aggregate statistics: last launch time, total play time,
/// launch count, last crash timestamp, and last change timestamp.
///
public final class LaunchHistoryPage extends Control implements DecoratorPage {

    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("launchhistory.title")));

    private final HMCLGameInstance instance;

    /// Creates a new launch history page for the given game instance.
    public LaunchHistoryPage(HMCLGameInstance instance) {
        getStyleClass().add("gray-background");
        this.instance = instance;
    }

    /// The game instance whose history is displayed.
    public HMCLGameInstance getInstance() {
        return instance;
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new LaunchHistoryPageSkin(this);
    }

    /// Builds a read-only stat row.
    private static LineTextPane statRow(String title, String value) {
        var row = new LineTextPane();
        row.setTitle(title);
        row.setText(value);
        row.setDisable(true);
        return row;
    }

    /// Formats a duration in milliseconds as a localized "Xd Xh Xm" string.
    private static String formatPlayTime(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        return i18n("launchhistory.playtime.format", days, hours, minutes);
    }

    /// Formats an instant as a localized date-time string, or returns a dash if null.
    private static String formatInstant(Instant instant) {
        return instant != null ? formatDateTime(instant) : "\u2014";
    }

    /// Formats an epoch-millis timestamp, or returns a dash if zero.
    private static String formatTimestamp(long millis) {
        return millis > 0 ? formatDateTime(Instant.ofEpochMilli(millis)) : "\u2014";
    }

    private static final class LaunchHistoryPageSkin extends SkinBase<LaunchHistoryPage> {

        private LaunchHistoryPageSkin(LaunchHistoryPage control) {
            super(control);

            LaunchHistory history = LaunchHistoryManager.INSTANCE.get(
                    control.getInstance().getId().toString());

            ComponentList stats = new ComponentList();
            stats.getContent().addAll(
                    statRow(i18n("launchhistory.last_launch"), formatTimestamp(history.lastLaunch())),
                    statRow(i18n("launchhistory.total_playtime"), formatPlayTime(history.totalPlayMs())),
                    statRow(i18n("launchhistory.launch_count"), String.valueOf(history.launchCount())),
                    statRow(i18n("launchhistory.last_crash"), formatInstant(history.lastCrash())),
                    statRow(i18n("launchhistory.last_change"), formatInstant(history.lastChange()))
            );

            VBox rootPane = new VBox(8);
            rootPane.setPadding(new Insets(16));
            rootPane.setFillWidth(true);
            rootPane.getChildren().addAll(
                    ComponentList.createComponentListTitle(i18n("launchhistory.info")),
                    stats
            );

            ScrollPane scrollPane = new ScrollPane(rootPane);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setStyle("-fx-background-color: transparent;");

            BorderPane pane = new BorderPane();
            pane.setCenter(scrollPane);
            getChildren().setAll(pane);
        }
    }
}
