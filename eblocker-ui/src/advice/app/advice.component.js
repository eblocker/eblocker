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
    templateUrl: 'app/advice.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        locale: '<'
    }
};

function Controller( $scope, $translate, $state, logger, security, APP_CONTEXT, LoadingService) { // jshint ignore: line
    'ngInject';

    const vm = this;

    LoadingService.isLoading(true);

    vm.handleBackgroundClick = handleBackgroundClick;

    vm.isLoading = function() {
        return LoadingService.isLoading();
    };

    function handleBackgroundClick() {
        // console.log('clicked on background.');
    }

    $scope.$on('IdleStart', function() {
        logger.info('IdleStart');
    });

    $scope.$on('IdleTimeout', function() {
        logger.info('IdleTimeout');
    });

    $scope.$on('IdleEnd', function() {
        logger.info('IdleEnd');
    });

    $scope.$on('Keepalive', function() {
        logger.info('Keepalive');
        security.requestToken(APP_CONTEXT.name);
    });
}
