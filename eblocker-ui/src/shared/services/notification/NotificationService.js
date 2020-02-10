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
export default function NotificationService($mdToast) {
    'ngInject';

    let hideDelay = 3000;

    function error(msgKey, data, parent, delay) {
        return $mdToast.show({
            controller: 'NotificationController',
            controllerAs: 'ctrl',
            templateUrl: 'services/notification/notification.html',
            hideDelay: angular.isNumber(delay) ? delay : 0,
            position: 'top right',
            parent: parent || getParent(),
            locals: {
                msgKey: msgKey,
                msgParams: {
                    status: angular.isDefined(data) ? data.status : undefined,
                    msg: angular.isDefined(data) ? data.statusText : undefined,
                    details: angular.isDefined(data) ? data.data : undefined
                }
            }
        });
    }

    function warning(msgKey, msgParams) {
        if (!angular.isDefined(msgParams)) {
            msgParams = {};
        }
        return $mdToast.show({
            controller: 'NotificationController',
            controllerAs: 'ctrl',
            templateUrl: 'services/notification/notification.html',
            hideDelay: hideDelay,
            position: 'top right',
            parent: getParent(),
            locals: {
                msgKey: msgKey,
                msgParams: msgParams
            }
        });
    }

    function info(msgKey, msgParams) {
        if (!angular.isDefined(msgParams)) {
            msgParams = {};
        }
        return $mdToast.show({
            controller: 'NotificationController',
            controllerAs: 'ctrl',
            templateUrl: 'services/notification/notification.html',
            hideDelay: hideDelay,
            position: 'top right',
            parent: getParent(),
            locals: {
                msgKey: msgKey,
                msgParams: msgParams
            }
        });
    }

    function getParent() {
        // if (angular.element(document).find('md-dialog').length > 0) {
        //     return angular.element(document).find('md-dialog')[0];
        // }
        return angular.element(document.body);
    }

    return {
        'info': info,
        'warning': warning,
        'error': error
    };
}
