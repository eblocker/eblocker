package org.eblocker.server.http.service;

import com.google.inject.Singleton;
import org.eblocker.server.common.network.Ipv4Cidr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates and canonicalizes CUSTOM WireGuard client routes.
 *
 * The limits are operational guardrails, not tunnel semantics. They are kept
 * in this policy component so the API layer and renderer can share the same
 * rules.
 */
@Singleton
public class WireGuardCustomRouteValidator {

    static final int MAX_ROUTES = 64;
    static final int MAX_TOTAL_INPUT_LENGTH = 4096;

    public List<String> normalize(
            List<String> routes) {

        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException(
                    "CUSTOM WireGuard routing requires at least one IPv4 CIDR."
            );
        }

        if (routes.size() > MAX_ROUTES) {
            throw new IllegalArgumentException(
                    "Too many CUSTOM WireGuard routes."
            );
        }

        int totalInputLength = 0;
        Set<String> normalized =
                new LinkedHashSet<>();

        for (String route : routes) {
            if (route == null) {
                throw new IllegalArgumentException(
                        "CUSTOM WireGuard routes must not contain null values."
                );
            }

            totalInputLength += route.length();

            if (totalInputLength > MAX_TOTAL_INPUT_LENGTH) {
                throw new IllegalArgumentException(
                        "CUSTOM WireGuard routes are too large."
                );
            }

            String candidate = route.trim();

            if (candidate.isEmpty()) {
                throw new IllegalArgumentException(
                        "CUSTOM WireGuard routes must not contain empty values."
                );
            }

            normalized.add(
                    Ipv4Cidr.parse(candidate).toString()
            );
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "CUSTOM WireGuard routing requires at least one IPv4 CIDR."
            );
        }

        return Collections.unmodifiableList(
                new ArrayList<>(normalized)
        );
    }
}
