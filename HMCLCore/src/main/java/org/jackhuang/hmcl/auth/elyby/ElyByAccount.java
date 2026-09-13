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

import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.NotLoggedInException;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactInfo;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.function.ExceptionalSupplier;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;

/// Account that authenticates through the Ely.by authentication service.
///
/// When the game is launched, `authlib-injector` is injected as a java agent so that
/// the client resolves profiles (and therefore skins) through the Ely.by session server
/// instead of the Mojang session server.
@NotNullByDefault
public class ElyByAccount extends YggdrasilAccount {
    /// Shared Yggdrasil service bound to the Ely.by authentication server.
    private static final YggdrasilService ELYBY_SERVICE = new YggdrasilService(new ElyByProvider());

    /// Authlib-injector meta endpoint of the Ely.by service.
    private static final String ELYBY_AUTHLIB_URL = "https://authserver.ely.by/api/authlib-injector";

    /// Authlib-injector server bound to the Ely.by service, used to prefetch meta before launching the game.
    private static final AuthlibInjectorServer ELYBY_SERVER = new AuthlibInjectorServer(ELYBY_AUTHLIB_URL);

    /// Provider of the bundled `authlib-injector` artifact used as the java agent.
    private final AuthlibInjectorArtifactProvider downloader;

    /// Creates a new Ely.by account by authenticating with the given credentials.
    ///
    /// @param username the user's nickname or E-mail
    /// @param password the user's password (optionally suffixed with `:token` for two-factor authentication)
    /// @param selector used to pick a character if the account has no selected profile
    /// @param downloader provider of the bundled `authlib-injector` artifact injected at launch
    public ElyByAccount(String username, String password, CharacterSelector selector, AuthlibInjectorArtifactProvider downloader) throws AuthenticationException {
        super(ELYBY_SERVICE, username, password, selector);
        this.downloader = downloader;
    }

    /// Restores an Ely.by account from stored credentials.
    public ElyByAccount(AccountID accountID, String username, YggdrasilSession session, AuthlibInjectorArtifactProvider downloader) {
        super(accountID, ELYBY_SERVICE, username, session);
        this.downloader = downloader;
    }

    @Override
    public synchronized AuthInfo logIn() throws AuthenticationException {
        return inject(super::logIn);
    }

    @Override
    public synchronized AuthInfo logInWithPassword(String password) throws AuthenticationException {
        return inject(() -> super.logInWithPassword(password));
    }

    @Override
    public AuthInfo playOffline() throws AuthenticationException {
        AuthInfo auth = super.playOffline();
        Optional<AuthlibInjectorArtifactInfo> artifact = downloader.getArtifactInfoImmediately();
        Optional<String> prefetchedMeta = ELYBY_SERVER.getMetadataResponse();

        if (artifact.isPresent() && prefetchedMeta.isPresent()) {
            return new ElyByAuthInfo(auth, artifact.get(), prefetchedMeta.get());
        } else {
            throw new NotLoggedInException();
        }
    }

    /// Authenticates with the Ely.by service and wraps the resulting [AuthInfo] so that the launched
    /// game carries the `authlib-injector` java agent bound to the Ely.by session server.
    private AuthInfo inject(ExceptionalSupplier<AuthInfo, AuthenticationException> loginAction) throws AuthenticationException {
        CompletableFuture<String> prefetchedMetaTask = CompletableFuture.supplyAsync(() -> {
            try {
                return ELYBY_SERVER.fetchMetadataResponse();
            } catch (IOException e) {
                throw new CompletionException(new ServerDisconnectException(e));
            }
        });

        CompletableFuture<AuthlibInjectorArtifactInfo> artifactTask = CompletableFuture.supplyAsync(() -> {
            try {
                return downloader.getArtifactInfo();
            } catch (IOException e) {
                throw new CompletionException(new ServerDisconnectException(e));
            }
        });

        AuthInfo auth = loginAction.get();

        String prefetchedMeta;
        AuthlibInjectorArtifactInfo artifact;

        try {
            prefetchedMeta = prefetchedMetaTask.get();
            artifact = artifactTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AuthenticationException) {
                throw (AuthenticationException) e.getCause();
            } else {
                throw new AuthenticationException(e.getCause());
            }
        }

        return new ElyByAuthInfo(auth, artifact, prefetchedMeta);
    }

    /// Returns the shared [YggdrasilService] bound to the Ely.by authentication server.
    public static YggdrasilService getService() {
        return ELYBY_SERVICE;
    }

    /// [AuthInfo] that appends the `authlib-injector` java agent arguments to the game launch.
    @NotNullByDefault
    private static class ElyByAuthInfo extends AuthInfo {

        private final AuthlibInjectorArtifactInfo artifact;
        private final String prefetchedMeta;

        public ElyByAuthInfo(AuthInfo authInfo, AuthlibInjectorArtifactInfo artifact, String prefetchedMeta) {
            super(authInfo.getUsername(), authInfo.getUUID(), authInfo.getAccessToken(), authInfo.getUserType(), authInfo.getUserProperties());

            this.artifact = artifact;
            this.prefetchedMeta = prefetchedMeta;
        }

        @Override
        public Arguments getLaunchArguments(LaunchOptions options) {
            return new Arguments().addJVMArguments(
                    "-javaagent:" + artifact.location() + "=" + ELYBY_AUTHLIB_URL,
                    "-Dauthlibinjector.side=client",
                    "-Dauthlibinjector.yggdrasil.prefetched=" + Base64.getEncoder().encodeToString(prefetchedMeta.getBytes(UTF_8)));
        }
    }
}