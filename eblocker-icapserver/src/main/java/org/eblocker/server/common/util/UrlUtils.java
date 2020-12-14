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
package org.eblocker.server.common.util;

import org.apache.commons.io.Charsets;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.eblocker.server.common.exceptions.EblockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlUtils {
    private static final Logger log = LoggerFactory.getLogger(UrlUtils.class);

    private static final String URL_HOSTNAME_REGEX = "^https?://(?:[^@/]+@)?([^:/]+).*";
    private static final Pattern URL_HOSTNAME_PATTERN = Pattern.compile(URL_HOSTNAME_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String CO_UK_STYLE_TDL_REGEX = ".*(\\.com?\\.[^.]+)$";
    private static final Pattern CO_UK_STYLE_TDL_PATTERN = Pattern.compile(CO_UK_STYLE_TDL_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String THREE_LEVEL_DOMAIN_REGEX = "(^|.*\\.)([^.]+\\.[^.]+\\.[^.]+)$";
    private static final Pattern THREE_LEVEL_DOMAIN_PATTERN = Pattern.compile(THREE_LEVEL_DOMAIN_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String TWO_LEVEL_DOMAIN_REGEX = "(^|.*\\.)([^.]+\\.[^.]+)$";
    private static final Pattern TWO_LEVEL_DOMAIN_PATTERN = Pattern.compile(TWO_LEVEL_DOMAIN_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String URL_WITHOUT_PROTOCOL_REGEX = "^([a-zA-Z0-9_-]+\\.)+[a-zA-Z0-9_-]+(:|/|$).*";
    private static final Pattern URL_WITHOUT_PROTOCOL_PATTERN = Pattern.compile(URL_WITHOUT_PROTOCOL_REGEX);

    private static final String DOMAIN_IN_STRING_REGEX = "^(?:https?://)?(?:[^@/\\n]+@)?([^:/\\n]+)";
    private static final Pattern DOMAIN_IN_STRING_PATTERN = Pattern.compile(DOMAIN_IN_STRING_REGEX);

    public static String getHostname(String urlString) {
        Matcher matcher = URL_HOSTNAME_PATTERN.matcher(urlString);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        throw new EblockerException("malformed url " + urlString);
    }

    public static boolean isUkStyleTdl(String hostname) {
        return CO_UK_STYLE_TDL_PATTERN.matcher(hostname).matches();
    }

    public static String getDomain(String hostname) {
        // TODO: since uk-style domains are not strictly mandatory anymore (i.e.
        // one can get a yoursite.uk-domain), do we need to keep this feature?
        if (isUkStyleTdl(hostname)) {
            return getDomain(hostname, THREE_LEVEL_DOMAIN_PATTERN);
        }
        return getDomain(hostname, TWO_LEVEL_DOMAIN_PATTERN);
    }

    private static String getDomain(String hostname, Pattern pattern) {
        Matcher matcher = pattern.matcher(hostname);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        log.debug("Cannot extract domain from hostname {} with regex {}", hostname, pattern);
        return null;
    }

    public static boolean isUrl(String url) {
        return url != null && URL_HOSTNAME_PATTERN.matcher(url).matches();
    }

    public static boolean isUrlWithoutProtocol(String url) {
        return url != null && URL_WITHOUT_PROTOCOL_PATTERN.matcher(url).matches();
    }

    public static String findUrlParameter(String requestUrl, String urlParam) {
        if (requestUrl == null) {
            return null;
        }
        if (urlParam == null) {
            //
            // Fallback to old procedure
            //
            return findTargetUrlInQueryParameter(requestUrl, urlParam);
        }
        String[] urlParams = urlParam.split("&");
        if (urlParams.length == 1) {
            //
            // Fallback to old procedure
            //
            return findTargetUrlInQueryParameter(requestUrl, urlParam);
        }

        TargetUrlParser targetUrlParser = TargetUrlParser.QUERY_PARAMETER;
        String targetUrlParserName = urlParams[0].toUpperCase();
        for (TargetUrlParser tup : TargetUrlParser.values()) {
            if (tup.name().equals(targetUrlParserName)) {
                targetUrlParser = tup;
                break;
            }
        }

        switch (targetUrlParser) {

            case END_OF_URL:
                return findTargetUrlAtEndOfUrl(requestUrl, urlParams[1]);

            case QUERY_PARAMETER:
            default:
                return findTargetUrlInQueryParameter(requestUrl, urlParams[1]);
        }
    }

    private enum TargetUrlParser {
        QUERY_PARAMETER,
        END_OF_URL,
        ;
    }

    private static String findTargetUrlAtEndOfUrl(String requestUrl, String marker) {
        int mark = requestUrl.lastIndexOf(marker);
        if (mark >= 0) {
            try {
                String urlString = requestUrl.substring(mark + marker.length());
                URL url = new URL(urlString);
                return url.toString();

            } catch (MalformedURLException e) {
                // silently ignore invalid URLs
            }
        }
        return null;
    }

    private static String findTargetUrlInQueryParameter(String requestUrl, String urlParam) {
        int firstQuestionMarkIndex = requestUrl.indexOf('?');
        if (firstQuestionMarkIndex != -1) {
            requestUrl = requestUrl.substring(firstQuestionMarkIndex + 1);
        }

        String targetUrlParam = null;
        URL targetUrl = null;

        for (NameValuePair entry : URLEncodedUtils.parse(requestUrl, Charsets.UTF_8)) {
            String name = entry.getName();
            String value = entry.getValue();
            if (urlParam != null && !urlParam.equals(name) || value == null) {
                // We have a tip for a certain param name, and this is not the correct one or it has no value
                continue;
            }

            String urlString = null;
            if (value.startsWith("http")) {
                urlString = value;
            } else if (urlParam != null) {
                if (value.startsWith("//")) {
                    urlString = "http:" + value;
                } else if (isUrlWithoutProtocol(value)) {
                    urlString = "http://" + value;
                }
            }
            if (urlString != null) {
                URL url;
                try {
                    url = new URL(urlString);
                } catch (MalformedURLException e) {
                    // silently ignore invalid URLs
                    continue;
                }
                if (targetUrl != null) {
                    log.info("Found two or more URL parameters in tracking URL: {}={} and {}={}", targetUrlParam, targetUrl, name, value);
                } else {
                    targetUrlParam = name;
                    targetUrl = url;

                }
            }
        }
        return (targetUrl == null ? null : targetUrl.toString());
    }

    public static boolean isSameDomain(String domain, String hostname) {
        return (hostname != null
                && domain != null
                && (
                hostname.equals(domain)
                        ||
                        hostname.length() > domain.length()
                                && hostname.endsWith(domain)
                                && hostname.charAt(hostname.length() - domain.length() - 1) == '.'
        )
        );
    }

    /**
     * Checks whether the domain part of this url is a just a Top Level Domain
     * (or invalid wildcard like '.'), which would be a big wildcard including
     * lots of domains, and should therefore be forbidden to add. Domains may be
     * given as bare domains (e.g. www.server.tld) but also as URLs as
     * copy-pasted from the browsers address bar (e.g.
     * https://user:pass@server.tld/folder/script.php?param=value&param2=value2)
     *
     * @param url The URL to check
     * @return true if the URL contains a valid domain
     */
    public static boolean isInvalidDomain(String url) {
        Matcher matcher = DOMAIN_IN_STRING_PATTERN.matcher(url);
        if (matcher.find()) {
            url = matcher.group(1);
            if (url != null) {
                // not allowed to begin with '.' or '-'
                if (url.startsWith(".") || url.startsWith("-")) {
                    return true;
                }
                String[] parts = url.split("\\.");
                boolean atLeastOneEmptyPart = false;
                for (String part : parts) {
                    if ("".equals(part)) {
                        atLeastOneEmptyPart = true;
                    }
                }
                return (parts.length < 2) || atLeastOneEmptyPart;
            }
        }
        return false;
    }

    public static String findDomainInString(String url) {
        Matcher matcher = DOMAIN_IN_STRING_PATTERN.matcher(url);
        if (matcher.find()) {
            url = matcher.group(1);
            if (url != null) {
                // not allowed to begin with '.' or '-'
                if (url.startsWith(".") || url.startsWith("-")) {
                    return null;
                }
                String[] parts = url.split("\\.");
                boolean atLeastOneEmptyPart = false;
                for (String part : parts) {
                    if ("".equals(part)) {
                        atLeastOneEmptyPart = true;
                    }
                }
                return ((parts.length < 2) || atLeastOneEmptyPart ? null : url);
            }
        }
        return null;
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("java does not support utf-8 ?", e);
        }
    }

    public static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("java does not support utf-8 ?", e);
        }
    }
}
