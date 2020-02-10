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
export default function AppController($scope, $state, $mdDialog, Idle, logger, security, APP_CONTEXT) {
    'ngInject';

    var vm = this;

    logger.info('Showing splash screen');

    var idleDialog;

    $scope.$on('IdleStart', function() {
        logger.info('IdleStart');
        idleDialog = $mdDialog.show({
            controller: 'IdleDialogController',
            controllerAs: 'idle',
            templateUrl: 'app/common/idle.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose:false
        })
        .then(function() {
            Idle.interrupt();
            idleDialog = undefined;
        }, function() {
            idleDialog = undefined;
        });

    });

    $scope.$on('IdleTimeout', function() {
        logger.info('IdleTimeout');
        if (angular.isDefined(idleDialog)) {
            $mdDialog.cancel();
        }
        $state.go('app.expired', {});
    });

    $scope.$on('IdleEnd', function() {
        logger.info('IdleEnd');
    });

    $scope.$on('Keepalive', function() {
        logger.info('Keepalive');
        security.requestToken(APP_CONTEXT.name);
    });

    $state.go('app.main', {}).catch(function(e) {
        console.error('Could not transition to app.main: ' + e);
    });
}

