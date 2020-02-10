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
export default function AddUserController(logger, $mdDialog, module, BlockerService, filterType,
                                          BLOCKER_TYPE) {
    'ngInject';

    const vm = this;

    vm.mode = {
        DOWNLOAD: 'DOWNLOAD_URL',
        CUSTOM: 'CUSTOM'
    };

    vm.title = module.name;
    vm.module = module;
    vm.isNew = !angular.isDefined(module.id);
    vm.type = vm.mode.CUSTOM;
    vm.formatList = BlockerService.getFormatList(BLOCKER_TYPE.DOMAIN);
    vm.format = vm.formatList[0];
    vm.updates = false;

    vm.submit = submit;
    vm.cancel = function() {
        $mdDialog.cancel();
    };

    function submit() {
        const isDownload = vm.type === vm.mode.DOWNLOAD;

        vm.filterListForm.name.$setValidity('unique', true);

        if (!vm.filterListForm.$valid) {
            return;
        } else {
            const newBlockerList = {
                name: {en: vm.module.localizedName, de: vm.module.localizedName},
                description: {en: vm.module.localizedDescription, de: vm.module.localizedDescription},
                category: 'PARENTAL_CONTROL',
                type: BLOCKER_TYPE.DOMAIN,
                providedByEblocker: false,
                enabled: true,
                url: isDownload ? vm.downloadUrl : undefined,
                content: isDownload ? undefined : vm.module.filteredDomains,
                filterType: vm.module.filterType,
                format: isDownload ? vm.format : 'DOMAINS',
                updateInterval: isDownload && vm.updates ? 'DAILY' : 'NEVER'
            };
            BlockerService.createBlocker(newBlockerList).then(function(module){
                $mdDialog.hide(module);
            }, function error(response) {
                logger.error('Unable to save download URL list', response);
            });
        }
    }
}
