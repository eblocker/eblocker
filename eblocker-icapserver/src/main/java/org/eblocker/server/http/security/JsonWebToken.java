/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.http.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public class JsonWebToken {

    private final String token;

    private final AppContext appContext;

    private final long expiresOn;

    private final boolean passwordRequired;

    @JsonCreator
    public JsonWebToken(
        @JsonProperty("token") String token,
        @JsonProperty("appContext") AppContext appContext,
        @JsonProperty("expiresOn") Long expiresOn,
        @JsonProperty("passwordRequired") Boolean passwordRequired) {
        this.token = token;
        this.appContext = appContext;
        this.expiresOn = expiresOn == null ? 0 : expiresOn;
        this.passwordRequired = passwordRequired == null ? true : passwordRequired;
    }

    public JsonWebToken(String token, Map<String, Object> claims, boolean passwordRequired) {
        this.token = token;
        expiresOn = claims.get("exp") == null ? 0 : ((Number) claims.get("exp")).longValue();
        appContext = AppContext.nullSafeValue(claims.get("acx").toString());
        this.passwordRequired = passwordRequired;
    }

    public String getToken() {
        return token;
    }

    public AppContext getAppContext() {
        return appContext;
    }

    public long getExpiresOn() {
        return expiresOn;
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JsonWebToken that = (JsonWebToken) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }
}
