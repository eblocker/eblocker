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
    templateUrl: 'app/components/ssl/trustedApps/trusted-apps-details.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

// jshint ignore: line
function Controller(logger, StateService, STATES, $stateParams, DialogService, TrustedAppsService, TableService, $q,
                    ArrayUtilsService, $window) { // jshint maxstatements:100
    'ngInject';
    'use strict';

    const vm = this;

    vm.maxLengthName = 50;
    vm.descriptionMaxLength = 150;
    vm.domainIpMaxLength = 2048;

    vm.toggleAppModule = toggleAppModule;
    vm.editable = editable;
    vm.editName = editName;
    vm.editDescription = editDescription;
    vm.editDomains = editDomains;
    vm.newDomain = newDomain;

    vm.backState = STATES.TRUSTED_APPS;
    vm.stateParams = $stateParams;

    vm.showFeedbackButton = showFeedbackButton;
    vm.openFeedback = openFeedback;

    vm.$onInit = function() {
        if (angular.isObject(vm.stateParams) && angular.isObject(vm.stateParams.param)) {
            vm.trustedApp = vm.stateParams.param.entry;
            updateDisplayData(vm.trustedApp);
        } else {
            StateService.goToState(vm.backState);
        }
    };

    function updateDisplayData(trustedApp) {
        vm.nameConfig = {
            value: trustedApp.name
        };

        vm.descriptionConfig = {
            value: trustedApp.localizedDescription
        };

        vm.tableData = formatDomains(trustedApp.domainsIps);
        vm.filteredTableData = angular.copy(vm.tableData);
    }

    function formatDomains(domains) {
        const lines = domains.split('\n');
        const ret = [];
        lines.forEach((line, index) => {
            ret.push({
                id: index,
                domainIp: line
            });
        });
        return ret;
    }

    function editable(app) {
        return !app.builtin;
    }

    function setModified(response) {
        vm.trustedApp = response.data;
    }

    function newDomain(event) {
        const msgKeys = {
            heading: 'ADMINCONSOLE.TRUSTED_APPS.DETAILS.HEADING_DOMAINS',
            label: 'ADMINCONSOLE.TRUSTED_APPS.DETAILS.LABEL_DOMAINS'
        };
        const subject = {value: [], id: vm.trustedApp.id};

        // event, subject, msgKeys, okAction, isUnique
        DialogService.
        openEditDomainsDialog(event, subject, msgKeys , addDomainsActionOk, vm.domainIpMaxLength).
        then(function success(subject) {
            // ** we need to update the label's value as well
            updateDisplayData(vm.trustedApp);
        });
    }

    function addDomainsActionOk(domainsIps) {
        if (domainIpsContain(vm.trustedApp.domainsIps, domainsIps)) {
            return $q.reject({data: domainsIps});
        }
        vm.trustedApp.domainsIps = vm.trustedApp.domainsIps !== '' ? vm.trustedApp.domainsIps.concat('\n') : '';
        vm.trustedApp.domainsIps = vm.trustedApp.domainsIps.concat(domainsIps);
        return TrustedAppsService.parseAndSave(vm.trustedApp).then(setModified);
    }

    function domainIpsContain(string, value) {
        const argument = value.split('\n');
        const list = string.split('\n');
        let contains = false;
        argument.forEach((domain) => {
            if (list.indexOf(domain.trim()) > -1) {
                contains = true;
            }
        });
        return contains;
    }


    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('ssl-trusted-apps-domains-table');

    vm.tableHeaderConfig = [
        {
            label: '',
            icon: '/img/icons/ic_apps_black.svg',
            isSortable: false,
            isXsColumn: true
        },
        {
            label: 'ADMINCONSOLE.TRUSTED_APPS.DETAILS.TABLE_DOMAIN.COLUMN.DOMAIN_IP',
            isSortable: true,
            sortingKey: 'domainIp',
        }
    ];

    vm.isSelectable = isSelectable;
    vm.isDeletable = isDeletable;
    vm.deleteDomains = deleteDomains;

    // Filtering following props of table entry
    vm.searchProps = ['domainIp'];

    function isSelectable(value) {
        return true;
    }

    function isDeletable(value) {
        return true;
    }

    function deleteDomains(values) {
        const domainIps = vm.trustedApp.domainsIps.split('\n');
        let ret = '';
        let counterDeleted = 0;
        domainIps.forEach((domain, index) => {
            if (!ArrayUtilsService.containsByProperty(values, 'domainIp', domain) ||
                domainIps.length - 1 === counterDeleted) {
                ret = ret.concat(ret === '' ? domain : '\n' + domain);
            } else {
                counterDeleted++;
            }
        });
        vm.trustedApp.domainsIps = ret;
        return TrustedAppsService.parseAndSave(vm.trustedApp).then(function success(response) {
            vm.trustedApp = response.data;
            updateDisplayData(vm.trustedApp);
            return {data: counterDeleted};
        });
    }
    // ## END: TABLE


    function editDomains(event, entry) {
        const msgKeys = {
            label: 'ADMINCONSOLE.TRUSTED_APPS.DETAILS.LABEL_DOMAINS'
        };
        const subject = {value: entry.domainsIps, id: entry.id};

        // event, subject, msgKeys, okAction, isUnique
        DialogService.
        openEditDomainsDialog(event, subject, msgKeys , editDomainsActionOk, vm.domainIpMaxLength).
        then(function success(subject) {
            // ** we need to update the label's value as well
            updateDisplayData(vm.trustedApp);
        });
    }

    function editDomainsActionOk(domainsIps) {
        vm.trustedApp.domainsIps = domainsIps;
        return TrustedAppsService.parseAndSave(vm.trustedApp).then(setModified);
    }

    function editDescription(event, entry) {
        const msgKeys = {
            label: 'ADMINCONSOLE.TRUSTED_APPS.DETAILS.LABEL_DESCRIPTION'
        };
        const subject = {value: entry.localizedDescription, id: entry.id};

        // event, subject, msgKeys, okAction, isUnique
        DialogService.
        openEditDialog(event, subject, msgKeys , editDescriptionActionOk, undefined, vm.descriptionMaxLength, 0).
        then(function success(subject) {
            // ** we need to update the label's value as well
            updateDisplayData(vm.trustedApp);
        });
    }

    function editDescriptionActionOk(localizedDescription) {
        vm.trustedApp.localizedDescription = localizedDescription;
        return TrustedAppsService.parseAndSave(vm.trustedApp).then(setModified);
    }

    function editName(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.TRUSTED_APPS.DETAILS.LABEL_NAME'
            };
            const subject = {value: entry.name, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editNameActionOk, undefined, vm.maxLengthName, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                updateDisplayData(vm.trustedApp);
            });
        }
    }

    function editNameActionOk(name) {
        vm.trustedApp.name = name;
        return TrustedAppsService.parseAndSave(vm.trustedApp).then(setModified);
    }

    function toggleAppModule(trustedApp){
        TrustedAppsService.toggleAppModule(trustedApp);
    }

    function showFeedbackButton(trustedApp) {
        if (!angular.isObject(trustedApp)) {
            return false;
        }
        if (trustedApp.id === 9997) { // Hide feedback link for ATA Auto Trust App
            return false;
        }
        return !trustedApp.builtin || trustedApp.modified;
    }

    function openFeedback(trustedApp) {
        if (!angular.isObject(trustedApp)) {
            return;
        }
        $window.open(feedbackLink(trustedApp));
    }

    function feedbackLink(trustedApp) {
        const subject = 'Suggestion for ' + (trustedApp.builtin ? 'existing' : 'new') + ' app: ' + trustedApp.name +
              (trustedApp.builtin ? ' (ID ' + trustedApp.id + ')' : '');
        const body = 'Name: ' + trustedApp.name +
              '\n\nDescription:\nde: ' + trustedApp.description.de + '\nen: ' + trustedApp.description.en +
              '\n\nDomains:\n' + trustedApp.domainsIps;
        return 'mailto:appfeedback@eblocker.org' +
            '?subject=' + encodeURIComponent(subject) +
            '&body=' + encodeURIComponent(body);
    }
}
