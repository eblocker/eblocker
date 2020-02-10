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
export default function TorVerifyConnectionDialogController($mdDialog, $window, $translate, TorService) {
    'ngInject';

    const vm = this;

    vm.checkSites = [];
    vm.closeDialog = closeDialog;
    vm.goToSite = goToSite;
    vm.langId = null;
    vm.isLoading = true;

    activate();

    function activate() {
        vm.langId = $translate.use();

        TorService.getTorCheckServices().then(function (response) {
            vm.checkSites = response.data;
        }).finally(function disableSpinner() {
            vm.isLoading = false;
        });
    }

    function closeDialog() {
        $mdDialog.hide();
    }

    function goToSite(checkSite) {
        let url = checkSite.url;
        if (checkSite.multiLingual && checkSite.multiLingual[vm.langId]) {
            url = checkSite.multiLingual[vm.langId];
        }
        $window.open(url);
        closeDialog();
    }
}
