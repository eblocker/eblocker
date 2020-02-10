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
    templateUrl: 'app/components/parentalControl/whitelists/whitelist-details.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

// jshint ignore: line
function Controller(logger, $translate, BlockerService, FilterService, StateService, STATES, $stateParams,
                    DialogService, BLOCKER_TYPE) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.descMaxLength = 2048;
    vm.maxLengthName = 50;
    vm.maxLengthDomain = 2048;
    vm.maxLengthUrl = 1024;

    vm.nameForm = {};
    vm.descriptionForm = {};
    vm.domainForm = {};

    vm.editName = editName;
    vm.editDescription = editDescription;
    vm.editDomains = editDomains;
    vm.editUrl = editUrl;
    vm.editFormat = editFormat;
    vm.editable = editable;

    vm.backState = STATES.WHITELIST;
    vm.stateParams = $stateParams;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry)) {
            vm.whitelist = $stateParams.param.entry;
        }

        if (!angular.isObject(vm.whitelist)) {
            StateService.goToState(vm.backState);
            return;
        }

        vm.isUrlBlocker = angular.isDefined(vm.whitelist.url);

        vm.whitelistDomains = {
            value: formatDomains(vm.whitelist.content)
        };

        vm.whitelistUrl = {
            value: vm.whitelist.url
        };

        vm.whitelistFormat = {
            value: $translate.instant('ADMINCONSOLE.FILTER_OVERVIEW.FORMAT_LIST.' +
                vm.whitelist.format)
        };

        vm.whitelistName = {
            value: vm.whitelist.name[$translate.use()]
        };
        vm.whitelistDescription = {
            value: vm.whitelist.description[$translate.use()]
        };
    };

    function editable(list) {
        return angular.isDefined(list) && !list.providedByEblocker;
    }

    function editUrl(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.WHITELISTS.DETAILS.LABEL_URL'
            };
            const subject = {value: entry.url, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editUrlActionOk, null, vm.maxLengthUrl, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.whitelistUrl.value = subject.value;
            });
        }
    }

    function editUrlActionOk(url) {
        vm.whitelist.url = url;
        return BlockerService.updateBlocker(vm.whitelist);
    }

    function editFormat(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.WHITELISTS.DETAILS.LABEL_FORMAT'
            };
            const subject = {value: entry.format, id: entry.id, type: BLOCKER_TYPE.DOMAIN};

            // event, subject, okAction
            DialogService.openEditFormatDialog(event, subject, editFormatActionOk).then(function success(subject) {
                // ** we need to update the label's value as well
                vm.whitelistFormat.value =
                    $translate.instant('ADMINCONSOLE.FILTER_OVERVIEW.FORMAT_LIST.' + subject.value);
            });
        }
    }

    function editFormatActionOk(format) {
        vm.whitelist.format = format;
        return BlockerService.updateBlocker(vm.whitelist);
    }

    function editDomains(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.WHITELISTS.DETAILS.LABEL_DOMAINS'
            };
            const subject = {value: entry.content, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDomainsDialog(event, subject, msgKeys , editDomainActionOk, vm.maxLengthDomain).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.whitelistDomains.value = formatDomains(subject.value);
            });
        }
    }

    function editDomainActionOk(domains) {
        vm.whitelist.content = domains;
        return BlockerService.updateBlocker(vm.whitelist);
    }

    function editDescription(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.WHITELISTS.DETAILS.LABEL_DESCRIPTION'
            };
            const subject = {value: entry.localizedDescription, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editDescriptionActionOk, undefined, vm.descMaxLength, 0).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.whitelistDescription.value = subject.value;
            });
        }
    }

    function editDescriptionActionOk(description) {
        vm.whitelist.description = {en: description, de: description};
        return BlockerService.updateBlocker(vm.whitelist);
    }

    function editName(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.WHITELISTS.DETAILS.LABEL_NAME'
            };
            const subject = {value: entry.localizedName, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editNameActionOk, editIsUnique, vm.maxLengthName, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.whitelistName.value = subject.value;
            });
        }
    }

    function editIsUnique(name, id) {
        // FIXME: implement in UI or leave it ..
        return true;//FilterService.uniqueName(name, id, 'whitelist');
    }

    function editNameActionOk(name) {
        vm.whitelist.name = {en: name, de: name};
        return BlockerService.updateBlocker(vm.whitelist);
    }

    function formatDomains(domains) {
        return angular.isString(domains) ? domains.split('\n').join(', ') : '';
    }
}
