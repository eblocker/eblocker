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
export default function ChangePinDialogController($scope, $mdDialog, os, browser) {
    'ngInject';

    let vm = this;

    vm.neverAgain = false;

    setOs(os);

    vm.browser = browser;

    function setOs(os) {
        if (os === 'mac') {
            vm.isMac = true;
        } else if (os === 'windows') {
            vm.isWin = true;
        } else if (os === 'ios' && (browser === 'safari' || browser === 'firefox')) {
            vm.isIos = true;
            vm.isSafari = browser === 'safari';
            vm.isFirefox = browser === 'firefox';
        } else if (os === 'android' && browser === 'firefox') {
            vm.isAndroid = true;
            vm.isFirefox = browser === 'firefox';
            vm.isChrome = browser === 'chrome';
        } else if (os === 'windows-phone') {
            // vm.isWinPhone = true;
            vm.other = true;
        } else {
            vm.other = true;
        }
    }

    vm.cancel = function() {
        $mdDialog.hide(vm.neverAgain);
    };
}
