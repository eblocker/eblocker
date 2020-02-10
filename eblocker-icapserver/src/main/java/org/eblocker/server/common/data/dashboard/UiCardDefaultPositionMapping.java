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
package org.eblocker.server.common.data.dashboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UiCardDefaultPositionMapping {
    private static Map<Integer, UiCardColumnPosition[]> mapping;

    public static Map<Integer, UiCardColumnPosition[]> get(List<UiCard> cards) {
        init(cards);
        return mapping;
    }

    private static void init(List<UiCard> cards) {
        mapping = new HashMap<>();
        cards.forEach((card) -> {
            mapping.put(card.getId(), getStaticPositions(card));
        });
    }

    public static UiCardColumnPosition[] getStaticPositions(UiCard card) {
        UiCardColumnPosition[] list = new UiCardColumnPosition[3];
        if (card.getName().equals("PAUSE")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 4, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 1, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 2, 1, true, true);
        } else if (card.getName().equals("ONLINE_TIME")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 1, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 1, 1, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 1, 1, true, true);
        } else if (card.getName().equals("SSL")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 13, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 1, 7, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 2, 5, true, true);
        } else if (card.getName().equals("MESSAGE")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 12, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 4, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 2, true, true);
        } else if (card.getName().equals("WHITELIST")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 11, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 6, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 3, true, true);
        } else if (card.getName().equals("CONSOLE")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 8, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 1, 4, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 1, 3, true, true);
        } else if (card.getName().equals("ICON")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 14, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 7, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 2, 6, true, true);
        } else if (card.getName().equals("DNS_STATISTICS")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 3, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 1, 2, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 1, 2, true, true);
        } else if (card.getName().equals("FILTER")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 9, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 5, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 4, true, true);
        } else if (card.getName().equals("WHITELIST_DNS")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 10, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 1, 6, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 4, true, true);
        } else if (card.getName().equals("MOBILE")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 7, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 5, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 2, 4, true, true);
        } else if (card.getName().equals("USER")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 6, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 3, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 2, 2, true, true);
        } else if (card.getName().equals("ANON")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 5, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 1, 3, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 1, true, true);
        } else if (card.getName().equals("BLOCKER_STATISTICS_TOTAL")) {
            list[0] = new UiCardColumnPosition(card.getId(), 1, 9, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 5, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 1, 4, true, true);
        } else if (card.getName().equals("CONNECTION_TEST")) {
            // connection test card should be expanded on small screen per default
            list[0] = new UiCardColumnPosition(card.getId(), 1, 15, true, true);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 1, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 1, true, true);
        } else { // all new cards are added on top
            list[0] = new UiCardColumnPosition(card.getId(), 1, 1, true, false);
            list[1] = new UiCardColumnPosition(card.getId(), 2, 2, true, true);
            list[2] = new UiCardColumnPosition(card.getId(), 3, 5, true, true);
        }
        return list;
    }

}
