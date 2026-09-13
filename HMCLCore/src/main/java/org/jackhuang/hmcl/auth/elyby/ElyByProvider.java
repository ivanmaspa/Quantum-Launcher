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
package org.jackhuang.hmcl.auth.elyby;

import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilProvider;
import org.jetbrains.annotations.NotNullByDefault;

import java.net.URI;
import java.util.UUID;

/**
 * Yggdrasil provider for the Ely.by authentication service.
 *
 * @see <a href="https://docs.ely.by/en/minecraft-auth.html">https://docs.ely.by/en/minecraft-auth.html</a>
 */
@NotNullByDefault
public class ElyByProvider implements YggdrasilProvider {
    /// Base URL of the Ely.by authentication service.
    private static final String API_ROOT = "https://authserver.ely.by/";

    /// URL of the authentication request (`POST /auth/authenticate`).
    private static final URI AUTHENTICATION_URL = URI.create(API_ROOT + "auth/authenticate");
    /// URL of the refresh request (`POST /auth/refresh`).
    private static final URI REFRESHMENT_URL = URI.create(API_ROOT + "auth/refresh");
    /// URL of the validation request (`POST /auth/validate`).
    private static final URI VALIDATION_URL = URI.create(API_ROOT + "auth/validate");
    /// URL of the invalidation request (`POST /auth/invalidate`).
    private static final URI INVALIDATION_URL = URI.create(API_ROOT + "auth/invalidate");

    @Override
    public URI getAuthenticationURL() {
        return AUTHENTICATION_URL;
    }

    @Override
    public URI getRefreshmentURL() {
        return REFRESHMENT_URL;
    }

    @Override
    public URI getValidationURL() {
        return VALIDATION_URL;
    }

    @Override
    public URI getInvalidationURL() {
        return INVALIDATION_URL;
    }

    @Override
    public URI getSkinUploadURL(UUID uuid) throws AuthenticationException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Ely.by does not support uploading skins through this API.");
    }

    @Override
    public URI getProfilePropertiesURL(UUID uuid) {
        return URI.create(API_ROOT + "session/profile/" + UUIDs.toCompactString(uuid));
    }
}