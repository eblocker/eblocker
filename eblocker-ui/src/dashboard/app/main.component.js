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
export default {
    templateUrl: 'app/main.component.html',
    controllerAs: 'vm',
    controller: MainController,
    bindings: {
        productInfo: '<',
        locale: '<',
        operatingUser: '<',
        device: '<'
    }
};

function MainController(logger, $document, $window, $timeout, $interval, $filter, DataService, CardService, DeviceService, // jshint ignore: line
                        UserService, SystemService, deviceDetector, DialogService, ResolutionService,
                        Idle, IDLE_TIMES) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.isTouchDevice = isTouchDevice;

    // Allows for smoother resizing:
    // only resize every n milliseconds (see timeout wrapper)
    let inProgress = false;
    let idleTimeout;

    function onBlurHandler(e) {
        const sec = IDLE_TIMES.IDLE / 2; // 600 seconds (+ 600 seconds while idle)
        if (angular.isUndefined(idleTimeout) && DataService.isRunning()) {
            logger.debug('Dashboard not active. Shutting services off in ' + sec + ' seconds. ', e);
            idleTimeout = $timeout(function () {
                DataService.off();
                idleTimeout = undefined;
            }, sec * 1000);
        }
    }

    function onFocusHandler(e) {
        if (angular.isDefined(idleTimeout)) {
            logger.debug('Dashboard active. Timer canceled ', e);
            $timeout.cancel(idleTimeout);
            idleTimeout = undefined;
        } else if (!DataService.isRunning()) {
            logger.debug('Dashboard active. Starting services.');
            DataService.on();
        }
    }

    function addIdleTimeoutEventListeners() {
        if (angular.isFunction($window.addEventListener)) {
            $window.addEventListener('blur', onBlurHandler);        // so that blur stops services (click on other tab)
            $window.addEventListener('focus', onFocusHandler);      // so that activation of tab starts services
        }
    }

    function removeIdleTimeoutEventListeners() {
        $window.removeEventListener('blur', onBlurHandler);
        $window.removeEventListener('focus', onFocusHandler);
    }

    vm.$onInit = function() {
        // Callback to execute when CardService makes a change from another component (dashboard.component.js has
        // dropdown list and updates the visibility. We want to react by re-rendering the dashboard).
        CardService.registerUpdateListener(updateVisibility);

        vm.initFinished = false;
        angular.element($window).on('resize', resizeWrapper);

        addIdleTimeoutEventListeners();

        // set initial screen resolution:
        // Prevents jitter when actually design doesn't change:
        // don't rearrange when numbers of column doesn't change
        vm.screenRes = getScreenSize();

        setDashboardScreenClass();

        if (vm.device.showBookmarkDialog) {
            DialogService.welcome(deviceDetector.os, deviceDetector.browser).then(function success(neverAgain){
                if (neverAgain) {
                    vm.device.showBookmarkDialog = false;
                    vm.device.showWelcomePage = false;
                    let flags = {
                        showBookmarkDialog: false,
                        showWelcomePage: false
                    };
                    DeviceService.updateShowWelcomeFlags(flags);
                }
                finishInit();
            });
        } else {
            finishInit();
        }

    };

    function finishInit() {
        getDashboardData(false).finally(function done() {
            normalizeColumnHeight();
            vm.initFinished = true;
        });
        DataService.on();
        startVisibleInterval();
    }

    vm.$onDestroy = function() {
        angular.element($window).off('resize', resizeWrapper);
        stopVisibleInterval();
        removeIdleTimeoutEventListeners();
        DataService.off();
    };

    function startVisibleInterval() {
        vm.visibleInterval = $interval(doUpdate, 3000);
    }

    function doUpdate() {
        // helper function, because otherwise param is interval counter (which results in reload being 1,2, -> truthy)
        getDashboardData(false);
    }

    function stopVisibleInterval() {
        if (angular.isDefined(vm.visibleInterval)) {
            $interval.cancel(vm.visibleInterval);
            vm.visibleInterval = undefined;
        }
    }

    function getDashboardData(reload) {
        const doReload = CardService.scheduleDashboardReload() || reload;
        CardService.scheduleDashboardReload(false);

        return CardService.getDashboardData(doReload, vm.productInfo).then(function success(hasChanged) {
            if (hasChanged || !angular.isArray(vm.columns) || vm.columns.length === 0) {
                vm.columns = CardService.getCardsByColumns();
            }
        });
    }

    function isTouchDevice() {
        return 'ontouchstart' in $window;
    }

    /**
     * Re-render the dashboard. Only called by eb-multiselect
     */
    function updateVisibility() {
        vm.columns = CardService.getCardsByColumns();
    }

    // ** Wraps the rearrange function in a timeout, so that for a better user experience
    // the dashboard is not constantly rearranged.
    function resizeWrapper() {
        if (!inProgress) {
            inProgress = true;
            // Timeout for smooth resizing (while inProgress we do not resize)
            $timeout(updateDashboardCardsOnResize, 300);
        }
    }

    function updateDashboardCardsOnResize() {
        const oldScreenRes = vm.screenRes;
        const newScreenRes = getScreenSize();
        if (oldScreenRes !== newScreenRes) {
            vm.screenRes = newScreenRes;
            vm.columns = CardService.getCardsByColumns();
            setDashboardScreenClass();
        }

        inProgress = false;
    }

    function setDashboardScreenClass() {
        const el = $document.find('dashboard-component')[0];
        if (angular.isObject(el)) {
            el.classList.remove('screen-size-xs',
                'screen-size-sm',
                'screen-size-mdsm',
                'screen-size-md',
                'screen-size-lg');
            el.classList.add('screen-size-' + ResolutionService.getScreenSize());
        }
    }

    function getScreenSize() {
        return ResolutionService.getScreenSize();
    }

    vm.getScreenClass = function () {
        return 'custom-column-' + vm.screenRes;
    };

    /**
     * Increase the column height of all columns to match the column with the largest height. This is needed
     * when the difference in height is very large and we want to drag a card from the bottom of the large column
     * into a column with a lot less height. We can then just drag the card to the left or right and drop it in the
     * other column w/o actually seeing the cards of the column with lesser height.
     */
    function normalizeColumnHeight() {
        const one =  getColumnElement(0);
        const two = getColumnElement(1);
        const three = getColumnElement(2);
        if (angular.isObject(one) && angular.isObject(two) && angular.isObject(three)) {
            const highest  = getHighest(one.scrollHeight, two.scrollHeight, three.scrollHeight) + 100;
            one.style.height = highest + 'px';
            two.style.height = highest + 'px';
            three.style.height = highest + 'px';
        }
    }

    function resetColumnHeight() {
        const one =  getColumnElement(0);
        const two = getColumnElement(1);
        const three = getColumnElement(2);
        if (angular.isObject(one) && angular.isObject(two) && angular.isObject(three)) {
            one.style.height = 'auto'; //originalHeight[0] + 'px';
            two.style.height = 'auto'; // originalHeight[1] + 'px';
            three.style.height = 'auto'; // originalHeight[2] + 'px';
        }
    }

    function getColumnElement(columnNumber) {
        return angular.element( document.getElementById( columnNumber + '') )[0];
    }

    function getHighest(one, two, three) {
        return [one, two, three].sort((a, b) => a - b)[2];
    }

    vm.staticOptions = {
        placeholder: 'card-placeholder',
        connectWith: '.cards-container',
        handle: '.cardHandle',
        start: function() {
            // console.log('Start event');
            normalizeColumnHeight();
        },
        update: function() {
            // console.log('Update event');
            /*
             * Called only when there is actually an update; drag/drop actually changed the array
             */
        },
        over: function() {
            // console.log('Over event');
            /*
             * Called when dragging over a dropzone
             */
        },
        stop:  function() {
            // console.log('Stop event');

            /*
             * ui.sortable directly orders the column (see ng-model).
             * We need to update the DashboardColumns of the current user as well.
             */
            $timeout(function() {
                saveNewDashboardOrder(CardService.getRepresentationOfCard(vm.columns));
                resetColumnHeight();
            }, 300);
        }
    };

    function saveNewDashboardOrder(dashboardCardColumns) {
        CardService.saveNewDashboardOrder(dashboardCardColumns);
    }

    vm.getCardObject = function(card) {
        return {
            html: card.input
        };
    };
}
