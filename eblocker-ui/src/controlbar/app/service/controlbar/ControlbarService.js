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
export default function ControlbarService() {
    'ngInject';

    let isOpenDropdownList = {};

    let alertMessagesNotification = true;

    function isDropdownOpen(bool, label) {
        if (angular.isUndefined(bool) && angular.isUndefined(label)) {
            return anyOpen();
        } else if (angular.isDefined(bool) && angular.isDefined(label)) {
            isOpenDropdownList[label] = bool;
        } else {
            // close all: defined case bool=false / label=undefined
            isOpenDropdownList = {};
        }
    }

    function anyOpen() {
        let open = false;
        Object.keys(isOpenDropdownList).forEach((isOpen) => {
            if (isOpenDropdownList[isOpen]) {
                open = true;
            }
        });
        return open;
    }

    function closeControlbar(icon) {
        let pos = 'close-eblocker-overlay';
        if (icon === 'right') {
            pos = 'close-eblocker-overlay-right';
        } else if (icon === 'left') {
            pos = 'close-eblocker-overlay-left';
        }
        let data = {
            'type': pos
        };
        window.parent.postMessage(data, '*');
    }

    function showAlertMessagesNotification(bool) {
        if (angular.isUndefined(bool)) {
            return alertMessagesNotification;
        }
        alertMessagesNotification = bool;
    }

    return {
        'isDropdownOpen': isDropdownOpen,
        'closeControlbar': closeControlbar,
        'showAlertMessagesNotification': showAlertMessagesNotification
    };
}
