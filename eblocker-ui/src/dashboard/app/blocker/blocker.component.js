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
    templateUrl: 'app/blocker/blocker.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        productInfo: '<',
        locale: '<'
    }
};

function Controller(logger, $state, $stateParams) {
    'ngInject';

    const vm = this;

    vm.$onInit = function() {
        if ($stateParams.type === 'ERR_SECURE_CONNECT_FAIL') {
            goToState('blockerSslWhitelisted');
        } else if ($stateParams.type === 'EBLKR_ACCESS_DENIED' || $stateParams.type === 'ERR_ACCESS_DENIED') {
            goToState('blockerAccessDenied');
        } else if ($stateParams.type === 'EBLKR_BLOCKED_MALWARE') {
            goToState('blockerMalware');
        } else if ($stateParams.type === 'EBLKR_BLOCKED_ADS_TRACKERS') {
            goToState('blockerAdsTrackers');
        } else if ($stateParams.type === 'EBLKR_BLOCKED_WHITELISTED') {
            goToState('blockerWhitelisted');
        } else {
            goToState('squidError');
        }
    };

    function goToState(name) {
        logger.debug('blocker state redirects to error: ', name,' (', $stateParams, ')');
        $state.transitionTo(name, $stateParams).catch(function(e) {
            logger.error('Could not transition to ' + name + ': ', e);
        });
    }

}
