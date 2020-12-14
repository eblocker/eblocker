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
package org.eblocker.server.icap.filter.bpjm;

import org.eblocker.server.common.util.ByteArrays;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BpjmFilter {

    public static final BpjmFilterDecision NOT_BLOCKED_DECISION = new BpjmFilterDecision(false, null, null, -1);

    /**
     * Regex for parsing urls
     * <p>
     * Source: https://tools.ietf.org/html/rfc3986#appendix-B
     * <table>
     *     <tr>
     *         <th>Component</th><th>Group</th>
     *     </tr>
     *     <tr>
     *         <td>scheme</td><td>2</td>
     *     </tr>
     *     <tr>
     *         <td>authority</td><td>4</td>
     *     </tr>
     *     <tr>
     *         <td>path</td><td>5</td>
     *     </tr>
     *     <tr>
     *         <td>query</td><td>7</td>
     *     </tr>
     *     <tr>
     *         <td>fragment</td><td>9</td>
     *     </tr>
     * </table>
     */
    private static final Pattern URL_PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");

    private final Map<ByteArrays.Key, BpjmEntry[]> entryByDomainHash = new HashMap<>();

    BpjmFilter(BpjmModul bpjmModul) {
        bpjmModul.getEntries().stream()
            .collect(Collectors.groupingBy(e -> new ByteArrays.Key(e.getDomainHash())))
            .forEach((k, v) -> entryByDomainHash.put(k, v.toArray(new BpjmEntry[0])));
    }

    public BpjmFilterDecision isBlocked(String url) {
        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            return NOT_BLOCKED_DECISION;
        }

        String host = matcher.group(4);
        List<Integer> indicies = indicesOf('.', host);
        if (indicies.isEmpty()) {
            return NOT_BLOCKED_DECISION;
        }

        String path = matcher.group(5);
        if (matcher.group(6) != null) { // bpjm filter may contain parameters, e.g. for youtube
            path += matcher.group(6);
        }
        indicies.add(0, -1);
        for (int i = indicies.size() - 2; i >= 0; --i) {
            String domain = host.substring(indicies.get(i) + 1);
            BpjmFilterDecision decision = isBlocked(domain, path);
            if (decision.isBlocked()) {
                return decision;
            }
        }

        return NOT_BLOCKED_DECISION;
    }

    private BpjmFilterDecision isBlocked(String domain, String path) {
        List<BpjmEntry> entries = getEntries(domain);
        if (entries.isEmpty()) {
            return NOT_BLOCKED_DECISION;
        }

        path = normalizePath(path);
        List<Integer> separatorIndices = indicesOf('/', path);
        BpjmEntry entry = find(path, separatorIndices.size(), entries);
        if (entry != null) {
            return new BpjmFilterDecision(true, domain, path, entry.getDepth());
        }

        for (int i = 0; i < separatorIndices.size(); ++i) {
            String partialPath = path.substring(0, separatorIndices.get(i) + 1);
            if (find(partialPath, i + 1, entries) != null) {
                return new BpjmFilterDecision(true, domain, path, i + 1);
            }
        }

        return NOT_BLOCKED_DECISION;
    }

    private List<BpjmEntry> getEntries(String domain) {
        byte[] httpHash = md5("http://" + domain);
        BpjmEntry[] httpEntries = entryByDomainHash.get(new ByteArrays.Key(httpHash));

        byte[] httpsHash = md5("https://" + domain);
        BpjmEntry[] httpsEntries = entryByDomainHash.get(new ByteArrays.Key(httpsHash));

        if (httpEntries == null && httpsEntries == null) {
            return Collections.emptyList();
        }

        if (httpEntries != null && httpsEntries == null) {
            return Arrays.asList(httpEntries);
        }

        if (httpEntries == null) {
            return Arrays.asList(httpsEntries);
        }

        List<BpjmEntry> entries = new ArrayList<>();
        entries.addAll(Arrays.asList(httpEntries));
        entries.addAll(Arrays.asList(httpsEntries));
        return entries;
    }

    private String normalizePath(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    private List<Integer> indicesOf(char separator, String value) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < value.length(); ++i) {
            if (value.charAt(i) == separator) {
                indices.add(i);
            }
        }
        return indices;
    }

    private BpjmEntry find(String path, int depth, List<BpjmEntry> entries) {
        byte[] hash = md5(path);
        return entries.stream()
            .filter(e -> e.getDepth() == depth)
            .filter(e -> Arrays.equals(e.getPathHash(), hash))
            .findFirst()
            .orElse(null);
    }

    private byte[] md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return digest.digest(value.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("implementation error", e);
        }
    }
}
