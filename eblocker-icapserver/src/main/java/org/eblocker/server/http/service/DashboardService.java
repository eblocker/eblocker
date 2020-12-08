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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.UserModuleOld;
import org.eblocker.server.common.data.dashboard.DashboardCard;
import org.eblocker.server.common.data.dashboard.DashboardCardPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* by DashboardCardService, move hard coded dashboard card instantiation into schema migration */
@Deprecated
public class DashboardService {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardService.class);

    private final DataSource dataSource;

    @Inject
    public DashboardService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void init() {
        LOG.info("DashboardService.init()");
        if (this.dataSource != null) {
            setAllUserCardsToVisible();
        }
    }

    /**
     * Make sure all cards of all users are visible w/o schema migration
     */
    private void setAllUserCardsToVisible() {
        List<UserModuleOld> users = dataSource.getAll(UserModuleOld.class);
        for (UserModuleOld user : users) {
            List<DashboardCard> newCards = new ArrayList<>();
            for (DashboardCard card : user.getDashboardCards()) {
                DashboardCard newCard = new DashboardCard(card.getId(), card.getRequiredFeature(), card.getTranslateSuffix(), card.getHtml(), card.isVisible(), false, card.getDefaultPos(), card.getCustomPos());
                newCards.add(newCard);
            }
            user.setDashboardCards(newCards);
            dataSource.save(user, user.getId());
        }
    }

    /**
     * Either all cards need to have a custom position set for specific number of columns or none.
     * We cannot have a mix of custom and default position usage, so that we do not assign too positions.
     * This method fixes this issue for new cards, where the user may have already re-ordered the old ones.
     * This would result in the new card not being shown in the UI (could be fixed in UI as well, but we should
     * initially set and save a custom position, if required).
     *
     * @return a dashboard card. The custom position is set or null, if no card has a custom position set
     */
    public DashboardCard normalizeCustomPosition(List<DashboardCard> cards, DashboardCard card) {
        DashboardCard normalizedCard = card;

        if (hasAnyCardCustomPositionForColumns(cards, 1) &&
            !hasCustomPositionForColumns(normalizedCard, 1)) {
            // new card does not have custom pos set for one-column-scenario (smallest screens)
            int newOrder = getMaxOrderNumberForColumns(cards, 1) + 1;
            normalizedCard = normalizeCustomPosForColumn(normalizedCard, 1, 1, newOrder);
        }

        if (hasAnyCardCustomPositionForColumns(cards, 2) &&
            !hasCustomPositionForColumns(normalizedCard, 2)) {
            // new card does not have custom pos set for two-column-scenario (medium screens)
            int newOrder = getMaxOrderNumberForColumns(cards, 2) + 1;
            normalizedCard = normalizeCustomPosForColumn(normalizedCard, 2, 1, newOrder);
        }

        if (hasAnyCardCustomPositionForColumns(cards, 3) &&
            !hasCustomPositionForColumns(normalizedCard, 3)) {
            // new card does not have custom pos set for three-column-scenario (large screens)
            int newOrder = getMaxOrderNumberForColumns(cards, 3) + 1;
            normalizedCard = normalizeCustomPosForColumn(normalizedCard, 3, 1, newOrder);
        }

        return normalizedCard;
    }

    /**
     * @param card         Card which custom position will be set
     * @param numOfColumns Current scenario: 1 (we have only one column, small screens), 2 (.. medium screens), 3 (three
     *                     columns, large screens)
     * @param newColumn    The column in which the card is sorted (e.g. for numOfColumns 3 newColumn can be 1-3.
     * @param newOrder     The highest order within the column, so that the card can be put at the end of the column
     * @return
     */
    private DashboardCard normalizeCustomPosForColumn(DashboardCard card, int numOfColumns, int newColumn, int newOrder) {
        DashboardCard ret = initCustomPos(card, numOfColumns);
        ret.getCustomPos()[numOfColumns - 1] = new DashboardCardPosition(newColumn, newOrder);
        return ret;
    }

    private DashboardCard initCustomPos(DashboardCard card, int numOfColumns) {
        return new DashboardCard(card.getId(),
            card.getRequiredFeature(),
            card.getTranslateSuffix(),
            card.getHtml(),
            card.isVisible(),
            card.isAlwaysVisible(),
            card.getDefaultPos(),
            new DashboardCardPosition[3]);
    }

    private int getMaxOrderNumberForColumns(List<DashboardCard> cards, int numOfColumns) {
        int order = 0; // start at zero, new card will get maxNum + 1
        for (DashboardCard card : cards) {
            if (hasCustomPositionForColumns(card, numOfColumns)) {
                // this must work; check beforehand if getCustomPos or getCustomPos()[numOfColumns - 1] are null
                int customOrder = card.getCustomPos()[numOfColumns - 1].getOrder();
                if (customOrder > order) {
                    order = customOrder;
                }
            }
        }
        return order;
    }

    private boolean hasAnyCardCustomPositionForColumns(List<DashboardCard> cards, int numOfColumns) {
        for (DashboardCard card : cards) {
            if (hasCustomPositionForColumns(card, numOfColumns)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCustomPositionForColumns(DashboardCard card, int numOfColumns) {
        DashboardCardPosition[] custPositions = card.getCustomPos();
        if (custPositions != null && custPositions.length == 3) {
            DashboardCardPosition custPos = custPositions[numOfColumns - 1]; // Columns counting: 1 - 3
            return custPos != null && custPos.getColumn() > 0 && custPos.getOrder() > 0;
        }
        return false;
    }

    public List<DashboardCard> generateDashboardCards() {
        return Arrays.asList(
            generatePauseCard(),
            generateConsoleCard(),
            generateMessageCard(),
            generateOnlineTimeCard(),
            generateSslCard(),
            generateWhitelistCard(),
            generateIconCard(),
            generateDnsStatisticsCard(),
            generateBlockerStatisticsTotalCard(),
            generateFilterCard(),
            generateWhitelistDnsCard(),
            generateEblockerMobileCard(),
            generateUserCard(),
            generateAnonCard());
    }

    private DashboardCard generatePauseCard() {
        int id = 1;
        String requiredFeature = ProductFeature.BAS.name();
        String translateSuffix = "PAUSE_CARD";
        String html = "<dashboard-pause></dashboard-pause>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 4),
            new DashboardCardPosition(2, 1),
            new DashboardCardPosition(2, 1)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateOnlineTimeCard() {
        int id = 2;
        String requiredFeature = ProductFeature.FAM.name();
        String translateSuffix = "ONLINE_TIME_CARD";
        String html = "<dashboard-online-time></dashboard-online-time>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 1),
            new DashboardCardPosition(1, 1),
            new DashboardCardPosition(1, 1)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateSslCard() {
        int id = 3;
        String requiredFeature = ProductFeature.PRO.name();
        String translateSuffix = "SSL_CARD";
        String html = "<dashboard-ssl></dashboard-ssl>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 13),
            new DashboardCardPosition(1, 7),
            new DashboardCardPosition(2, 5)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateMessageCard() {
        int id = 4;
        String requiredFeature = ProductFeature.WOL.name();
        String translateSuffix = "MESSAGE_CARD";
        String html = "<dashboard-message></dashboard-message>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 12),
            new DashboardCardPosition(2, 4),
            new DashboardCardPosition(3, 2)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateWhitelistCard() {
        int id = 5;
        String requiredFeature = ProductFeature.PRO.name();
        String translateSuffix = "WHITELIST_CARD";
        String html = "<dashboard-whitelist></dashboard-whitelist>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 11),
            new DashboardCardPosition(2, 6),
            new DashboardCardPosition(3, 3)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateConsoleCard() {
        int id = 6;
        String requiredFeature = ProductFeature.WOL.name();
        String translateSuffix = "CONSOLE_CARD";
        String html = "<dashboard-console></dashboard-console>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 8),
            new DashboardCardPosition(1, 4),
            new DashboardCardPosition(1, 3)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    public DashboardCard generateIconCard() {
        int id = 7;
        String requiredFeature = ProductFeature.WOL.name();
        String translateSuffix = "ICON_CARD";
        String html = "<dashboard-icon></dashboard-icon>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 14),
            new DashboardCardPosition(2, 7),
            new DashboardCardPosition(2, 6)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    /**
     * 2 or 3 Columns: upper left
     * 1 column: on top of statistics total card
     */
    public DashboardCard generateDnsStatisticsCard() {
        int id = 8;
        String requiredFeature = ProductFeature.PRO.name();
        String translateSuffix = "DNS_STATISTICS_CARD";
        String html = "<dashboard-filter-statistics></dashboard-filter-statistics>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 3),
            new DashboardCardPosition(1, 2),
            new DashboardCardPosition(1, 2)
        };
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    /*
     * This card is currently not visible in the UI. Only the admin is allowed to make the change settings that
     * can be changed with this card.
     */
    public DashboardCard generateFilterCard() {
        int id = 9;
        String requiredFeature = ProductFeature.PRO.name();
        String translateSuffix = "FILTER_CARD";
        String html = "<dashboard-filter></dashboard-filter>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 9),
            new DashboardCardPosition(2, 5),
            new DashboardCardPosition(3, 4)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    public DashboardCard generateWhitelistDnsCard() {
        int id = 10;
        String requiredFeature = ProductFeature.PRO.name();
        String translateSuffix = "WHITELIST_DNS_CARD";
        String html = "<dashboard-whitelist-dns></dashboard-whitelist-dns>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 10),
            new DashboardCardPosition(1, 6),
            new DashboardCardPosition(3, 4)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    public DashboardCard generateEblockerMobileCard() {
        int id = 11;
        String requiredFeature = ProductFeature.BAS.name();
        String translateSuffix = "MOBILE_CARD";
        String html = "<dashboard-mobile></dashboard-mobile>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 7),
            new DashboardCardPosition(2, 5),
            new DashboardCardPosition(2, 4)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateUserCard() {
        int id = 12;
        String requiredFeature = ProductFeature.FAM.name();
        String translateSuffix = "USER_CARD";
        String html = "<dashboard-user></dashboard-user>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 6),
            new DashboardCardPosition(2, 3),
            new DashboardCardPosition(2, 2)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    private DashboardCard generateAnonCard() {
        int id = 13;
        String requiredFeature = "BAS";
        String translateSuffix = "ANON_CARD";
        String html = "<dashboard-anonymization></dashboard-anonymization>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 5),
            new DashboardCardPosition(1, 3),
            new DashboardCardPosition(3, 1)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    /**
     * 2 or 3 Columns: upper right
     * 1 column: below statistics card
     */
    public DashboardCard generateBlockerStatisticsTotalCard() {
        int id = 14;
        String requiredFeature = "PRO";
        String translateSuffix = "BLOCKER_STATISTICS_TOTAL_CARD";
        String html = "<dashboard-filter-statistics-total></dashboard-filter-statistics-total>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 9),
            new DashboardCardPosition(2, 5),
            new DashboardCardPosition(1, 4)
        };
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }

    // TODO WILL BE IMPLEMENTED SOON: positions are already according to EB1-1883
    public DashboardCard generateSearchCard() {
        int id = 15;
        String requiredFeature = ProductFeature.BAS.name();
        String translateSuffix = "SEARCH_CARD";
        String html = "<dashboard-search></dashboard-search>";
        boolean visibility = true;
        boolean alwaysVisible = false;

        DashboardCardPosition[] defaultPos = {
            new DashboardCardPosition(1, 2),
            new DashboardCardPosition(2, 2),
            new DashboardCardPosition(2, 3)};
        DashboardCardPosition[] customPos = null;

        return new DashboardCard(id, requiredFeature, translateSuffix, html, visibility, alwaysVisible, defaultPos, customPos);
    }
}
