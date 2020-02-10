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
export default function ActionController(logger, $state, $stateParams, $http) {
    'ngInject';
    'use strict';
    const vm = this;

    vm.activatePauseAndGoToDashboard = function() {
        $http.post('/api/device/pause', {'pausing': 300})
            .then(function success(response) {
                return response;
        }, function error(response) {
            logger.error('Server call failed: response ', response);
        }).finally(function goToRealApp() {
            // $state.transitionTo('app', {param: {anchor: 'card-1'}}).catch(function(e) {
            $state.transitionTo('main', {anchor: 'PAUSE'}).catch(function(e) {
                logger.error('Could not transition to main: ' + e);
            });
        });
    };

    vm.goToDashboardCard = function(cardName) {
        $state.transitionTo('main', {anchor: cardName}).catch(function(e) {
            logger.error('Could not transition to main (card ID ' + cardName + '): ', e);
        });
    };

    vm.goToDashboard = function() {
        $state.transitionTo('main').catch(function(e) {
            logger.error('Could not transition to main: ', e);
        });
    };

    vm.goToMobileWizard = function () {
        $state.transitionTo('mobile').catch(function(e) {
            logger.error('Could not transition to mobile: ', e);
        });
    };

    if ($stateParams.action === 'pause') {
        $http.defaults.headers.common['Authorization'] = 'Bearer ' + $stateParams.urlToken;
        vm.activatePauseAndGoToDashboard();
    } else if ($stateParams.action === 'scroll') {
        vm.goToDashboardCard($stateParams.urlToken);
    } else if ($stateParams.action === 'wizard') {
        if ($stateParams.urlToken === 'eblockerMobile') {
            vm.goToMobileWizard();
        } else {
            logger.warn('Unknown wizard ', $stateParams);
        }
    } else if ($stateParams.action === 'app' && $stateParams.urlToken === 'main') {
        // URL used to be dashboard/#!/app/main. Handle old bookmarks.
        logger.debug('Old link detected ', $stateParams);
        vm.goToDashboard();
    } else {
        logger.warn('Unknown action ', $stateParams);
        vm.goToDashboard();
    }
}
