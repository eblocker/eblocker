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

import java.util.regex.Pattern;

public enum AppContext {

    //
    // No password required; only specific routes are allowed
    //
    PUBLIC(false, "^static.route$", "^public\\..*", "^errorpage\\..*"),

    //
    // Password (might be) required; all routes are allowed
    //
    CONSOLE(true),

    ADMINCONSOLE(true, "^adminconsole\\..*"),

    ADVICE(false, "^advice\\..*"),

    // TODO: Define necessary routes, and use this appContext consequently
    CONTROLBAR(false, "^controlbar\\..*", "^errorpageAuthenticated\\..*"),

    //TODO: Define necessary routes for squid errors
    SQUID_ERROR(false, "^errorpageAuthenticated\\..*", "^errorpageExclusive\\..*"),

    //TODO: Define necessary routes for system context
    SYSTEM(false),

    //
    // No password required; only dashboard routes are allowed
    //
    DASHBOARD(false, "^dashboard\\..*");

    private final boolean passwordRequired;

    private final Pattern[] routePatterns;

    AppContext(boolean passwordRequired, String... routeRegexs) {
        this.passwordRequired = passwordRequired;
        this.routePatterns = new Pattern[routeRegexs.length];
        for (int i = 0; i < routePatterns.length; i++) {
            this.routePatterns[i] = Pattern.compile(routeRegexs[i]);
        }
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    public boolean isValidContextFor(String route) {
        //
        // For backwards compatibility: If no rule is specified, everything is allowed
        //
        if (routePatterns == null || routePatterns.length == 0) {
            return true;
        }

        //
        // Check if given route matches the app context
        //
        for (Pattern routePattern : routePatterns) {
            if (routePattern.matcher(route).matches()) {
                return true;
            }
        }
        return false;
    }

    public static AppContext defaultValue() {
        return CONTROLBAR;
    }

    public static AppContext nullSafeValue(String acx) {
        if (acx == null) {
            return defaultValue();
        }
        for (AppContext appContext : AppContext.values()) {
            if (acx.equalsIgnoreCase(appContext.name())) {
                return appContext;
            }
        }
        return defaultValue();
    }
}
