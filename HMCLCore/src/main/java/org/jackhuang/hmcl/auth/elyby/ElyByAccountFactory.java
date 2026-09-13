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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorArtifactProvider;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Map;
import java.util.Objects;

/// Factory that creates and restores [ElyByAccount] instances.
@NotNullByDefault
public class ElyByAccountFactory extends AccountFactory<ElyByAccount> {
    private final AuthlibInjectorArtifactProvider downloader;

    public ElyByAccountFactory(AuthlibInjectorArtifactProvider downloader) {
        this.downloader = downloader;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME_PASSWORD;
    }

    @Override
    public ElyByAccount create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);

        return new ElyByAccount(username, password, selector, downloader);
    }

    @Override
    public ElyByAccount fromStorage(JsonObject metadata, JsonObject privateData) {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(privateData);

        AccountID accountID = Account.readAccountID(metadata);
        YggdrasilSession session = YggdrasilSession.fromStorage(metadata, privateData);

        String loginName = JsonUtils.getString(metadata, "loginName");
        if (loginName == null) {
            throw new IllegalArgumentException("storage does not have loginName");
        }

        if (privateData.get("profileProperties") instanceof JsonObject profilePropertiesObject) {
            Map<String, String> properties = JsonUtils.GSON.fromJson(
                    profilePropertiesObject,
                    JsonUtils.mapTypeOf(String.class, String.class));
            GameProfile selected = session.getSelectedProfile();
            ElyByAccount.getService().getProfileRepository().put(selected.getId(), new CompleteGameProfile(selected, properties));
            ElyByAccount.getService().getProfileRepository().invalidate(selected.getId());
        }

        return new ElyByAccount(accountID, loginName, session, downloader);
    }
}