/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.common.blocker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Provides static utility methods for converting domain and pattern filters
 * to blockers.
 */
public class BlockerUtils {
    private static final Logger log = LoggerFactory.getLogger(BlockerUtils.class);

    static Blocker mapDefinition(ExternalDefinition definition, Long lastUpdate, boolean enabled) {
        return new Blocker(definition.getId(),
                localizedMap(definition.getName()),
                localizedMap(definition.getDescription()),
                mapType(definition.getType()),
                definition.getCategory(),
                lastUpdate,
                false,
                definition.getUrl(),
                readContent(definition),
                definition.getFormat(),
                null,
                definition.getUpdateInterval(),
                definition.getUpdateStatus(),
                enabled,
                definition.getFilterType());
    }

    private static String readContent(ExternalDefinition definition) {
        if (definition.getUrl() != null) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(Paths.get(definition.getFile())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read content of blocker {}", definition.getId(), e);
            return null;
        }
    }

    static Map<String, String> localizedMap(String value) {
        Map<String, String> languageMap = new HashMap<>();
        languageMap.put("en", value);
        languageMap.put("de", value);
        return languageMap;
    }

    static <U, V> V firstValue(Map<U, V> map) {
        if (map == null) {
            return null;
        }

        Iterator<V> it = map.values().iterator();
        return it.hasNext() ? it.next() : null;
    }

    private static BlockerType mapType(Type type) {
        switch (type) {
            case DOMAIN:
                return BlockerType.DOMAIN;
            case MALWARE_URL:
            case PATTERN:
                return BlockerType.PATTERN;
            default:
                throw new IllegalArgumentException("can not map blocker " + type + " to type");
        }
    }

    static Type mapBlockerType(BlockerType type) {
        switch (type) {
            case DOMAIN:
                return Type.DOMAIN;
            case PATTERN:
                return Type.PATTERN;
            default:
                throw new IllegalArgumentException("can not map " + type + " to blocker type");
        }
    }

    static Category mapDomainFilterCategory(org.eblocker.server.common.data.parentalcontrol.Category category) {
        switch (category) {
            case ADS:
                return Category.ADS;
            case CUSTOM:
                return Category.CUSTOM;
            case MALWARE:
                return Category.MALWARE;
            case PARENTAL_CONTROL:
                return Category.PARENTAL_CONTROL;
            case TRACKERS:
                return Category.TRACKER;
            default:
                return null;
        }
    }

    static org.eblocker.server.common.data.parentalcontrol.Category mapToDomainFilterCategory(Category category) {
        switch (category) {
            case ADS:
                return org.eblocker.server.common.data.parentalcontrol.Category.ADS;
            case CUSTOM:
                return org.eblocker.server.common.data.parentalcontrol.Category.CUSTOM;
            case MALWARE:
                return org.eblocker.server.common.data.parentalcontrol.Category.MALWARE;
            case PARENTAL_CONTROL:
                return org.eblocker.server.common.data.parentalcontrol.Category.PARENTAL_CONTROL;
            case TRACKER:
                return org.eblocker.server.common.data.parentalcontrol.Category.TRACKERS;
            default:
                throw new IllegalArgumentException("no domain filter category available for " + category);
        }
    }

    static Category mapPatternFilterCategory(org.eblocker.server.icap.filter.Category category) {
        switch (category) {
            case ADS:
                return Category.ADS;
            case TRACKER_BLOCKER:
                return Category.TRACKER;
            case CONTENT:
                return Category.CONTENT;
            default:
                return null;
        }
    }

    static org.eblocker.server.icap.filter.Category mapToPatternFilterCategory(Category category) {
        switch (category) {
            case ADS:
                return org.eblocker.server.icap.filter.Category.ADS;
            case TRACKER:
                return org.eblocker.server.icap.filter.Category.TRACKER_BLOCKER;
            case MALWARE:
                return org.eblocker.server.icap.filter.Category.MALWARE;
            case CONTENT:
                return org.eblocker.server.icap.filter.Category.CONTENT;
            default:
                throw new IllegalArgumentException("pattern filters not available for " + category);
        }
    }
}
