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
    templateUrl: 'app/components/parentalControl/blacklists/blacklist-details.component.html',
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

    vm.backState = STATES.BLACKLIST;
    vm.stateParams = $stateParams;

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry)) {
            vm.blacklist = $stateParams.param.entry;
        }

        if (!angular.isObject(vm.blacklist)) {
            StateService.goToState(vm.backState);
            return;
        }

        vm.isUrlBlocker = angular.isDefined(vm.blacklist.url);

        vm.blacklistDomains = {
            value: formatDomains(vm.blacklist.content)
        };

        vm.blacklistUrl = {
            value: vm.blacklist.url
        };

        vm.blacklistFormat = {
            value: $translate.instant('ADMINCONSOLE.FILTER_OVERVIEW.FORMAT_LIST.' +
                vm.blacklist.format)
        };

        vm.blacklistName = {
            value: vm.blacklist.name[$translate.use()]
        };
        vm.blacklistDescription = {
            value: vm.blacklist.description[$translate.use()]
        };
    };

    function editable(list) {
        return angular.isDefined(list) && !list.providedByEblocker;
    }

    function editUrl(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.BLACKLISTS.DETAILS.LABEL_URL'
            };
            const subject = {value: entry.url, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editUrlActionOk, null, vm.maxLengthUrl, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.blacklistUrl.value = subject.value;
            });
        }
    }

    function editUrlActionOk(url) {
        vm.blacklist.url = url;
        return BlockerService.updateBlocker(vm.blacklist);
    }

    function editFormat(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.BLACKLISTS.DETAILS.LABEL_FORMAT'
            };
            const subject = {value: entry.format, id: entry.id, type: BLOCKER_TYPE.DOMAIN};

            // event, subject, okAction
            DialogService.openEditFormatDialog(event, subject, editFormatActionOk).then(function success(subject) {
                // ** we need to update the label's value as well
                vm.blacklistFormat.value =
                    $translate.instant('ADMINCONSOLE.FILTER_OVERVIEW.FORMAT_LIST.' + subject.value);
            });
        }
    }

    function editFormatActionOk(format) {
        vm.blacklist.format = format;
        return BlockerService.updateBlocker(vm.blacklist);
    }

    function editDomains(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.BLACKLISTS.DETAILS.LABEL_DOMAINS'
            };
            const subject = {value: entry.content, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDomainsDialog(event, subject, msgKeys , editDomainActionOk, vm.maxLengthDomain).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.blacklistDomains.value = formatDomains(subject.value);
            });
        }
    }

    function editDomainActionOk(domains) {
        vm.blacklist.content = domains;
        return BlockerService.updateBlocker(vm.blacklist);
    }

    function editDescription(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.BLACKLISTS.DETAILS.LABEL_DESCRIPTION'
            };
            const subject = {value: entry.localizedDescription, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editDescriptionActionOk, undefined, vm.descMaxLength, 0).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.blacklistDescription.value = subject.value;
            });
        }
    }

    function editDescriptionActionOk(description) {
        vm.blacklist.description = {en: description, de: description};
        return BlockerService.updateBlocker(vm.blacklist);
    }

    function editName(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.BLACKLISTS.DETAILS.LABEL_NAME'
            };
            const subject = {value: entry.localizedName, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editNameActionOk, editIsUnique, vm.maxLengthName, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.blacklistName.value = subject.value;
            });
        }
    }

    function editIsUnique(name, id) {
        // FIXME: implement in UI or leave it ..
        return true;//FilterService.uniqueName(name, id, 'blacklist');
    }

    function editNameActionOk(name) {
        vm.blacklist.name = {en: name, de: name};
        return BlockerService.updateBlocker(vm.blacklist);
    }

    function formatDomains(domains) {
        return angular.isString(domains) ? domains.split('\n').join(', ') : '';
    }
}
