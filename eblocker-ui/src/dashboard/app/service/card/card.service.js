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
export default function CardService($http, logger, $q, $location, $anchorScroll, $filter, $interval, // jshint ignore: line
                                    CARD_HTML, ArrayUtilsService, DataCachingService,
                                    CardAvailabilityService, UserService, ResolutionService) {
    'ngInject';

    const PATH = '/api/dashboardcard';
    const PATH_COLUMNS = '/api/dashboardcard/columns';

    let cardCache, cardColumnsCache, forceReload, syncTimer;

    function startSyncTimer(interval) {
        if (!angular.isDefined(syncTimer) && angular.isNumber(interval)) {
            syncTimer = $interval(syncData, interval);
        } else if (!angular.isNumber(interval)) {
            logger.warn('Cannot start synch timer with interval ', interval);
        }
    }

    function stopSyncTimer() {
        if (angular.isDefined(syncTimer)) {
            $interval.cancel(syncTimer);
            syncTimer = undefined;
        }
    }

    function syncData() {
        getCards(true);
        getCardColumns(true);
    }

    function getCards(reload) {
        cardCache = DataCachingService.loadCache(cardCache, PATH, reload).then(function success(response) {

            const cards = response.data;

            logger.info('Received dashboard cards: ', cards);

            // add sorting string so that cards can be sorted alphabetically in dropdown list
            if (angular.isArray(cards)) {
                cards.forEach((card) => {
                    card.sortingString = card.name + '.CARD.TITLE';
                });
            } else {
                logger.error('Unable to get dashboard cards', response);
            }

            return updateCardsTitleParam(cards);
        }, function error(response) {
            logger.error('Failed to call getCards: ', response);
            return $q.reject(response);
        });
        return cardCache;
    }

    function updateCardsTitleParam(cards) {
        return UserService.getUsers(false).then(function() {
            cards.forEach((card) => {
                if (angular.isDefined(card.referencingUserId)) {
                    const user = UserService.getUserById(card.referencingUserId);
                    // the user may have been deleted, so it may be undefined
                    if (angular.isObject(user)) {
                        card.titleParam = user.name;
                    } else {
                        // a user has been deleted, so reload the cards.
                        forceReload = true;
                    }
                }
            });
            return {data: cards};
        });
    }

    function getCardColumns(reload) {
        cardColumnsCache = DataCachingService.loadCache(cardColumnsCache, PATH_COLUMNS, reload).
        then(function success(response) {
            logger.info('Received dashboard card columns: ', response.data);
            return response;
        }, function error(response) {
            logger.error('Failed to call getCardColumns: ', response);
            return $q.reject(response);
        });
        return cardColumnsCache;
    }

    let ALL_CARDS, CARDS_COLUMNS;

    function getDashboardData(reload, productInfo) {
        const reloadAll = forceReload || reload;
        forceReload = false;
        return $q.all([
            getCards(reloadAll),
            getCardColumns(reloadAll),
            CardAvailabilityService.updateData()
        ]).then(function success(responses) {
            if (angular.isArray(ALL_CARDS) && responses[0].data.length === ALL_CARDS.length) {
                // case: ALL_CARDS already set, there may be changes or not
                const TMP = setGlobalDisplayProperties(ALL_CARDS, productInfo);
                const changeDetected = hasAnyGlobalDisplayPropertyChanged(ALL_CARDS, TMP);
                if (changeDetected) {
                    ALL_CARDS = angular.copy(TMP);
                }
                return changeDetected;
            } else {
                // case: ALL_CARDS not set, there are changes
                ALL_CARDS = setGlobalDisplayProperties(addHtmlTags(responses[0].data), productInfo);
                CARDS_COLUMNS = responses[1].data;
                return true;
            }
        });
    }

    function getAllCards() {
        return ALL_CARDS || [];
    }

    /**
     * Determine and set if a card is visible in general (e.g. Online time is only available if user has time
     * restrictions).
     * @param cards
     * @param productInfo
     * @returns {*}
     */
    function setGlobalDisplayProperties(cards, productInfo) {
        const ret = angular.copy(cards);
        ret.forEach((card) => {
            card.displayGlobally = CardAvailabilityService.isCardAvailable(card, productInfo);
            // pause card should always be visible in dropdown
            card.showInMenuButDisable = CardAvailabilityService.onlyShowInDropdown(card);
            card.tooltip = CardAvailabilityService.getTooltipForDisableState(card);
        });
        return ret;
    }

    /**
     * Compare two lists (of cards) if the displayGlobally property of *any* card has changed. If so,
     * a rerender of the dashboard is required.
     * @param list1
     * @param list2
     * @returns {boolean}
     */
    function hasAnyGlobalDisplayPropertyChanged(list1, list2) {
        if (list1.length !== list2.length) {
            throw new Error('Dashboard card list do not have the same size.');
        }
        let changeDetected = false;

        list1.forEach((item) => {
            changeDetected = ArrayUtilsService.getItemBy(list2, 'id', item.id).displayGlobally !==
                item.displayGlobally || changeDetected;
        });
        return changeDetected;
    }

    function saveNewDashboardOrder(columns) {
        return $http.put(PATH_COLUMNS, columns).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error while updating dashboard card columns: ', response);
            return response;
        });
    }

    /**
     * We need the correct UI component for each dashboard card. To avoid having HTML code on the dashboard model,
     * we use the translation string to get the HTML from CARD_HTML constants.
     * @param cards
     * @returns {Array}
     */
    function addHtmlTags(cards) {
        const ret = [];
        cards.forEach(function(card) {
            const uiCard = angular.copy(card);
            uiCard.input = CARD_HTML[card.name].replace('<CARD_ID_PLACEHOLDER>', card.id);
            if (angular.isDefined(card.referencingUserId)) {
                /**
                 * Of some cards we create multiple instances (e.g. parental control card). Each of these instances
                 * needs to have a reference which card it actually belongs to (e.g. based on referencingUserId).
                 * So the UI component of the card has a binding (userId) which is set here. By that binding
                 * the component can get the user that it actually represents.
                 *
                 * See app/_bootstrap/_constants/cardHtml.js
                 */
                uiCard.input = uiCard.input.replace('<USER_ID_PLACEHOLDER>', card.referencingUserId);
            }
            ret.push(uiCard);
        });
        return ret;
    }

    /**
     * Here we want to convert the UI dashboard structure (which card is in which column in what order)
     * and turn it into the DashboardCardColumns structure, that is saved on the user.
     *
     * This function is required to save the dashboard layout (position of cards) for the user.
     *
     * @param columnsOfCards model by which the dashboard displays the cards.
     * columnsOfCards is a list that contains a list for each column.
     * In each column-list are the actual dashboard card models.
     * @returns {*}
     */
    function getRepresentationOfCard(columnsOfCards) {
        /*
         * columnsOfCards.length: which columned-view is updated? oneColumn, twoColumn or threeColumn
         */
        if (columnsOfCards.length === 1) {
            CARDS_COLUMNS.oneColumn = setCardsNewColumn(CARDS_COLUMNS.oneColumn, columnsOfCards);
        } else if (columnsOfCards.length === 2) {
            CARDS_COLUMNS.twoColumn = setCardsNewColumn(CARDS_COLUMNS.twoColumn, columnsOfCards);
        } else if (columnsOfCards.length === 3) {
            CARDS_COLUMNS.threeColumn = setCardsNewColumn(CARDS_COLUMNS.threeColumn, columnsOfCards);
        }
        return CARDS_COLUMNS;
    }

    function setCardsNewColumn(cardColumnRep, columnsOfCards) {
        const ret = [];
        columnsOfCards.forEach((column, index) => {
            column.forEach((card, order) => {
                ret.push(
                    {
                        id: card.id,
                        column: index + 1, // account for index starting at zero
                        index: order,
                        visible: card.visible,
                        expanded: getCardRepresentationById(card.id, CARDS_COLUMNS).expanded
                    }
                );
            });
        });
        return ret;
    }

    function getCardsByColumns() {
        const columns = [];
        if (isOneColumn()) {
            columns.push(getSortedListForColumn(CARDS_COLUMNS.oneColumn, ALL_CARDS, 1));
        } else if (isTwoColumn()) {
            columns.push(getSortedListForColumn(CARDS_COLUMNS.twoColumn, ALL_CARDS, 1));
            columns.push(getSortedListForColumn(CARDS_COLUMNS.twoColumn, ALL_CARDS, 2));
        } else if (isThreeColumn()) {
            columns.push(getSortedListForColumn(CARDS_COLUMNS.threeColumn, ALL_CARDS, 1));
            columns.push(getSortedListForColumn(CARDS_COLUMNS.threeColumn, ALL_CARDS, 2));
            columns.push(getSortedListForColumn(CARDS_COLUMNS.threeColumn, ALL_CARDS, 3));
        }
        return columns;
    }

    function isOneColumn() {
        const screenSize = ResolutionService.getScreenSize();
        return screenSize === 'xs' || screenSize === 'sm' || screenSize === 'mdsm';
    }

    function isTwoColumn() {
        return ResolutionService.getScreenSize() === 'md';
    }

    function isThreeColumn() {
        return ResolutionService.getScreenSize() === 'lg';
    }

    function getSortedListForColumn(cardRepresentations, allCards, column) {
        // relevant for twoColumn / threeColumn since all columns are in one list:
        // filter relevant column 1 OR 2 (two columns) or 1, 2 OR 3 (threeColumns)
        const filteredList = ArrayUtilsService.sortByProperty($filter('filter')(cardRepresentations, function(filter) {
            return filter.column === column;
        }), 'index');

        const cardList = [];

        filteredList.forEach((rep) => {
            // If card is not found for some reason:
            // This should not happen, if all users have all cards. Child users do not have all cards. That *should*
            // be correctly reflected in the representation object. So the card representation in the user should
            // not contain a card that is not returned by the server (allCards). An inconsistency here should
            // be solved by the server. But in case there is something wrong,
            // we can only add cards that are actually available, so we check if the card exists first.
            const card = ArrayUtilsService.getItemBy(allCards, 'id', rep.id);
            if (angular.isObject(card)) {
                card.visible = rep.visible;
                cardList.push(card);
            } else {
                logger.warn('Unable to find card with ID ' + rep.id);
            }

        });
        return cardList;
    }

    let isReloadDashboard = false;

    function scheduleDashboardReload(bool) {
        if (bool === true || bool === false) {
            isReloadDashboard = bool;
        }
        return isReloadDashboard;
    }

    let anchorScrollString;

    /**
     * Sets the anchor scroll string, which is then called by the cards to scroll to right height.
     * @param string
     * @returns {*}
     */
    function anchorScroll(string) {
        if (angular.isString(string)) {
            anchorScrollString = string;
        }
        return anchorScrollString;
    }

    /**
     * Uses card name in query param 'anchor' to scroll to card. E.g. '/#!/main?anchor=MOBILE_CARD'
     *
     * It should be by name and not by ID, so we can actually use the URL to say 'scroll to mobile card', otherwise
     * we would have to find out the ID first to say 'scroll to card number 11'.
     *
     * This only works on cards that call this function within in their postLink callback. This makes sure that
     * the card is rendered before calling the anchorScroll function.
     *
     * The HTML-ID-tag is set in main.component.html (see ngRepeat). That way the $anchorScroll service
     * can scroll to the correct card.
     */
    function scrollToCard(name) {
        const scrollConst = anchorScroll();
        if (angular.isString(scrollConst) && scrollConst === name) {
            // set hash to allow scrolling to anchor / id
            $location.hash(scrollConst);
            // actually scroll to anchor
            $anchorScroll();
        }
    }

    function getUpdateColumns(columns, card) {
        return {
            oneColumn: updateColumn(columns.oneColumn, card),
            twoColumn: updateColumn(columns.twoColumn, card),
            threeColumn: updateColumn(columns.threeColumn, card)

        };
    }

    function updateColumn(column, card) {
        const newColumn = angular.copy(column);
        newColumn.forEach((c) => {
            if (c.id === card.id) {
                c.visible = card.visible;
            }
        });
        return newColumn;
    }

    let visibilityCallback;

    /**
     * Allows to update the visibility of dashboard cards: dropdown allows user to show/hide cards.
     * @param card
     */
    function updateVisibility(card) {
        if (angular.isFunction(visibilityCallback)) {
            // update card UI model
            card.visible = !card.visible;

            // update UI model representation, but only the n-th column view:
            // CARDS_COLUMNS['oneColumn']: card-representations for one column
            // CARDS_COLUMNS['twoColumn']: card-representations for two columns
            // CARDS_COLUMNS['threeColumn']: card-representations for three columns
            CARDS_COLUMNS[getColumnPropNameByNumColumns()] =
                updateColumn(CARDS_COLUMNS[getColumnPropNameByNumColumns()], card);

            // save UI model representation
            saveNewDashboardOrder(CARDS_COLUMNS);

            // notify listeners
            visibilityCallback();
        }
    }

    /*
     * Allow to rerender the dashboard (by setting vm.columns in main.component.js)
     * when user changes visibility of cards in toolbar (parent state)
     */
    function registerUpdateListener(fn) {
        visibilityCallback = fn;
    }

    /**
     * Set and get expanded / collapsed state of a dashboard card
     * @param cardId
     * @param bool
     * @returns {*}
     */
    function isExpanded(cardId, bool) {
        if (angular.isDefined(bool)) {
            // setter option

            if (isOneColumn()) {
                // Case XS: only one card expanded at a time
                // -> first set all to false, then set the bool of cardId (collapse all exect the one we expand)
                // -> or we are collapsing the card 'cardId' in which case we still want to collapse all..
                ALL_CARDS.forEach(card => {
                    setCardRepresentationById(card.id, false, CARDS_COLUMNS);
                });
            }

            // Now set the value for card 'cardId' in the cards representations
            setCardRepresentationById(cardId, bool, CARDS_COLUMNS);
            saveNewDashboardOrder(CARDS_COLUMNS);
        } else {
            const rep = getCardRepresentationById(cardId, CARDS_COLUMNS);
            // either the value is defined or the card is expanded on large screens and collapsed on small ones
            return angular.isObject(rep) && angular.isDefined(rep.expanded) ? rep.expanded : !isOneColumn();
        }
    }

    function getColumnPropNameByNumColumns() {
        if (isOneColumn()) {
            return 'oneColumn';
        } else if (isTwoColumn()) {
            return 'twoColumn';
        }
        return'threeColumn';
    }

    function getCardRepresentationById(id, columns) {
        const prop = getColumnPropNameByNumColumns();
        const numId = Number(id);
        return ArrayUtilsService.getItemBy(columns[prop], 'id', numId);
    }

    /**
     * Sets the expanded state of the dashboard card representation object
     * @param id
     * @param bool
     * @param columns
     */
    function setCardRepresentationById(id, bool, columns) {
        const prop = getColumnPropNameByNumColumns();
        if (columns.hasOwnProperty(prop)) {
            columns[prop].forEach(rep => {
                if (rep.id.toString() === id.toString()) {
                    rep.expanded = bool;
                }
            });
        }
    }

    function toggleExpandCollapseAll() {
        const bool = isAtLeastOneCollapsed();
        ALL_CARDS.forEach(card => {
            setCardRepresentationById(card.id, bool, CARDS_COLUMNS);
        });
        saveNewDashboardOrder(CARDS_COLUMNS);
    }

    function isAtLeastOneCollapsed() {
        const prop = getColumnPropNameByNumColumns();
        return CARDS_COLUMNS.hasOwnProperty(prop) ? checkForCollapsedCard(CARDS_COLUMNS[prop]) : false;
    }

    /**
     * Looking for one collapsed (not expanded) card representation by reduce method:
     * - if a is object, it should not be expanded
     * - if a is not object, it is result from prior reduce iteration and we simply "keep it" (the '&& a' bit)
     * -> either if false from prior iteration or if object, but expanded, we check if b is not expanded
     * -> then return the value to next reduce iteration
     *
     * @returns true, that is "carried" through all iterations. Otherwise false.
     */
    function checkForCollapsedCard(columns) {
        return columns.reduce((a, b) => {
            return ((angular.isObject(a) && !a.expanded) || (!angular.isObject(a) && a) ) || !b.expanded;
        }, columns[0]);
    }

    return {
        start: startSyncTimer,
        stop: stopSyncTimer,
        anchorScroll: anchorScroll,
        getAllCards: getAllCards,
        getCardsByColumns: getCardsByColumns,
        getDashboardData: getDashboardData,
        getRepresentationOfCard: getRepresentationOfCard,
        registerUpdateListener: registerUpdateListener,
        scheduleDashboardReload: scheduleDashboardReload,
        saveNewDashboardOrder: saveNewDashboardOrder,
        scrollToCard: scrollToCard,
        updateVisibility: updateVisibility,
        isExpanded: isExpanded,
        isAtLeastOneCollapsed: isAtLeastOneCollapsed,
        checkForCollapsedCard: checkForCollapsedCard, // for testability
        toggleExpandCollapseAll: toggleExpandCollapseAll
    };
}
