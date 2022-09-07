package com.tribe29;

import static com.google.gerrit.json.OutputFormat.JSON;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.GroupDescription.Basic;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.extensions.auth.oauth.OAuthToken;
import com.google.gerrit.extensions.auth.oauth.OAuthTokenEncrypter;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AbstractGroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.codec.binary.Base64;

@Singleton
public class KeycloakGroup extends AbstractGroupBackend {

  private static final String UUID_PREFIX = "keycloak:";
  private static final String NAME_PREFIX = "keycloak/";

  private final DynamicItem<OAuthTokenEncrypter> encrypter;
  private final AuthConfig authConfig;

  private final Cache<Account.Id, OAuthToken> cache;

  private final Random random;

  @Inject
  public KeycloakGroup(@Named("oauth_tokens") Cache<Id, OAuthToken> cache,
      DynamicItem<OAuthTokenEncrypter> encrypter, AuthConfig authConfig) {
    this.encrypter = encrypter;
    this.cache = cache;
    this.authConfig = authConfig;
    this.random = new Random();
  }

  @Override
  public boolean handles(UUID uuid) {
    return authConfig.isOAuthType() && uuid.get().startsWith(UUID_PREFIX);
  }

  @Override
  public Basic get(UUID uuid) {
    return new Basic() {
      @Override
      public UUID getGroupUUID() {
        return uuid;
      }

      @Override
      public String getName() {
        return NAME_PREFIX + uuid.get().replace(UUID_PREFIX, "");
      }

      @Override
      public String getEmailAddress() {
        return null;
      }

      @Override
      public String getUrl() {
        return null;
      }
    };
  }

  @Override
  public Collection<GroupReference> suggest(String name, ProjectState project) {
    return Collections.emptyList();
  }

  @Override
  public GroupMembership membershipsOf(CurrentUser user) {
    OAuthToken accessToken = cache.getIfPresent(user.getAccountId());
    if (accessToken == null) {
      return null;
    }
    accessToken = decrypt(accessToken);
    if (accessToken.isExpired()) {
      cache.invalidate(user.getAccountId());
      return null;
    }

    String parsedJWT = "";

    try {
      parsedJWT = parseJwt(accessToken.getToken());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }

    JsonObject tokenJson = JSON.newGson().fromJson(parsedJWT, JsonElement.class).getAsJsonObject();
    return new ListGroupMembership(
        StreamSupport.stream(tokenJson.get("group_membership").getAsJsonArray().spliterator(),
                false)
            .map(element -> UUID.parse(UUID_PREFIX + element.getAsString()))
            .collect(Collectors.toList()));
  }

  @Override
  public boolean isVisibleToAll(UUID uuid) {
    return true;
  }

  private OAuthToken decrypt(OAuthToken token) {
    OAuthTokenEncrypter enc = encrypter.get();
    if (enc == null) {
      return token;
    }
    return enc.decrypt(token);
  }

  private String parseJwt(String input) throws UnsupportedEncodingException {
    String[] parts = input.split("\\.");
    Preconditions.checkState(parts.length == 3);
    Preconditions.checkNotNull(parts[1]);
    return new String(Base64.decodeBase64(parts[1]), StandardCharsets.UTF_8.name());
  }
}
