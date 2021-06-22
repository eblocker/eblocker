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
    templateUrl: 'app/cards/domainBlocklist/domain-blocklist-table.component.html',
    controller: DomainBlocklistTableController,
    controllerAs: 'vm',
    bindings: {
        domains: '<',
        onUpdate: '&',
        placeholder: '@'
    }
};

function DomainBlocklistTableController(logger, ArrayUtilsService) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.clickOnDomainCheckbox = clickOnDomainCheckbox;
    vm.clickAddDomainButton = clickAddDomainButton;
    vm.isAddButtonDisabled = isAddButtonDisabled;
    vm.isEntryWhitelisted = isEntryWhitelisted;

    vm.searchProps = ['domain'];
    vm.searchTerm = '';

    vm.$onChanges = function(changes) {
        if (changes.domains) {
            updateTableData(getDomainsAsObjects(vm.domains));
        }
    };

    function isAddButtonDisabled() {
        return !angular.isArray(vm.tableData) ||
            ArrayUtilsService.containsByProperty(vm.tableData, 'domain', vm.searchTerm) ||
            vm.searchTerm === '';
    }

    function isEntryWhitelisted(entry) {
        return entry._checked;
    }

    function updateTableData(domains) {
        vm.tableData = [];
        domains.forEach((domain, index) => {
            const entry = {
                id: index,
                domain: domain.domain,
                _checked: domain._checked
            };
        vm.tableData.push(entry);
    });
        vm.tableData = ArrayUtilsService.sortByProperty(vm.tableData, 'domain');
        vm.filteredTableData = angular.copy(vm.tableData);
    }

    // ** click on domain checkbox
    function clickOnDomainCheckbox(entry) {
        const index = ArrayUtilsService.getIndexOf(vm.tableData, entry, 'id');
        vm.tableData[index]._checked = entry._checked;
        updateFilterlist(vm.tableData);
    }

    // ** click on add-button
    function clickAddDomainButton(domain) {

        // ** when user hits enter-key for search field
        if (!angular.isString(domain) ||
            domain === ''  ||
            ArrayUtilsService.containsByProperty(vm.tableData, 'domain', domain)) {
            return;
        }

        vm.tableData.push({id: -1, domain: domain, _checked: true});

        updateFilterlist(vm.tableData).then(function() {
            clearSearchField();
            updateTableData(vm.tableData);
        });
    }

    function getDomainsAsObjects(domains) {
        const table = [];
        domains.forEach((domain, index) => {
            table.push({
            id: index,
            domain: domain,
            _checked: true
        });
    });
        return table;
    }

    function updateFilterlist(tableData) {
        const domains = [];

        tableData.forEach((domain) => {
            if (domain._checked) {
            domains.push(domain.domain);
        }});

        return vm.onUpdate({domains: domains});
    }

    function clearSearchField() {
        vm.searchTerm = '';
    }
}
