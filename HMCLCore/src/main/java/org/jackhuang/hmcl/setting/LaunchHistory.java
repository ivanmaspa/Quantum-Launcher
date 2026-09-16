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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/// Launch history snapshot for a single game instance.
///
/// All timestamps are in UTC.
///
/// @param lastLaunch  epoch-millis timestamp of the most recent launch start
/// @param totalPlayMs cumulative play time in milliseconds
/// @param launchCount total number of launches recorded
/// @param lastCrash   timestamp of the most recent abnormal exit, or `null` if never crashed
/// @param lastChange  timestamp of the most recent instance change, or `null` if never changed
@NotNullByDefault
public record LaunchHistory(
        long lastLaunch,
        long totalPlayMs,
        int launchCount,
        @Nullable Instant lastCrash,
        @Nullable Instant lastChange
) {

    /// Empty launch history for instances with no recorded data.
    public static final LaunchHistory EMPTY = new LaunchHistory(0L, 0L, 0, null, null);
}
