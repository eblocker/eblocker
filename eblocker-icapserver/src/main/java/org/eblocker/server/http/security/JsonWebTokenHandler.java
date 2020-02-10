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

import com.auth0.jwt.JWTSigner;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.JWTVerifyException;
import org.eblocker.server.common.network.BaseURLs;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.restexpress.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Singleton
public class JsonWebTokenHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JsonWebTokenHandler.class);

    private final long tokenSquidValiditySeconds;
    private final long tokenSystemValiditySeconds;

    private final String secret;
    private final String issuer;
    private final String audience;

    @Inject
    public JsonWebTokenHandler(
            @Named("authentication.token.squid.validity.seconds") long tokenSquidValiditySeconds,
            @Named("authentication.token.system.validity.seconds") long tokenSystemValiditySeconds,
            BaseURLs baseURLs
    ) {
        this.tokenSquidValiditySeconds = tokenSquidValiditySeconds;
        this.tokenSystemValiditySeconds = tokenSystemValiditySeconds;
        this.issuer = baseURLs.getHttpsURL();
        this.audience = baseURLs.getHttpsURL();

        LOG.info("Using random value as base for JWT secret");
        secret = UUID.randomUUID().toString();
    }

    public TokenInfo verifyToken(String encodedToken) {
        try {
            JWTVerifier jwtVerifier = new JWTVerifier(secret, audience, issuer);
            Map<String,Object> claims = jwtVerifier.verify(encodedToken);
            return new TokenInfo(claims);

        } catch (JWTVerifyException | SignatureException e) {
            throw new UnauthorizedException("error.token.invalid", e);

        } catch (IllegalStateException | GeneralSecurityException | IOException e) {
            LOG.info("Received corrupted authn token: {}", e.getMessage());
            throw new UnauthorizedException("error.token.corrupt", e);

        }
    }

    public JsonWebToken generateToken(boolean passwordRequired, AppContext context, long tokenValiditySeconds, boolean isAuthenticationValid) {
        long iat = nowSeconds();
        long exp = iat + tokenValiditySeconds;

        JWTSigner signer = new JWTSigner(secret);
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("exp", exp);
        claims.put("iat", iat);
        claims.put("acx", context);
        claims.put("aut", isAuthenticationValid);

        String jwt = signer.sign(claims);

        return new JsonWebToken(jwt, claims, passwordRequired);
    }

    public JsonWebToken generateSystemToken() {
        return generateToken(false, AppContext.SYSTEM, tokenSystemValiditySeconds, true);
    }

    public JsonWebToken generateSquidToken() {
        return generateToken(false, AppContext.SQUID_ERROR, tokenSquidValiditySeconds, true);
    }

    private long nowSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

 }
