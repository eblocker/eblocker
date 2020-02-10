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
export default function EditTorCountryListController(logger, $scope, $mdDialog, $window, TorService, countryList,
                                                     selectedList, NotificationService) {
    'ngInject';

    const vm = this;
    vm.apply = apply;
    vm.selectAllExitNodes = selectAllExitNodes;
    vm.deselectAllExitNodes = deselectAllExitNodes;
    vm.isSmallScreen = function() {
        return $window.innerWidth <= 600;
    };

    vm.checkCountry = checkCountry;
    vm.noEntrySelected = noEntrySelected;

    function setTorCountries(countries, selected) {
        const ret = [];
        countries.forEach((c) => {
            if (selected.indexOf(c.code) < 0) {
                c._checked = false;
                ret.push(c);
            }
        });
        return ret;
    }

    function getSelectedNodes(list) {
        // ** get all selected countries
        const ret = list.filter(function(e) {
            return e._checked;
        });
        return ret;
    }

    function convertToCodeList(list) {
        const ret = [];
        list.forEach((country) =>{
            ret.push(country.code);
        });
        return ret;
    }

    function checkCountry(country) {
        country._checked = !country._checked;
    }

    function noEntrySelected() {
        return vm.filteredTorCountries.filter(function (e) {
            return e._checked;
        }).length === 0;
    }

    function apply() {
        if (noEntrySelected()) {
            return;
        }
        updateTorExitNodes();
    }

    //apply selected tor exit node countries
    function updateTorExitNodes() {
        const selectedNodes = convertToCodeList(getSelectedNodes(vm.filteredTorCountries));
        // ** also add previous selected countries, which were not included into selection list
        selectedList.forEach((country) =>{
            selectedNodes.push(country);
        });
        TorService.updateSelectedTorExitNodes(selectedNodes).then(function success(response) {
            logger.info('Successfully updated Tor exit nodes.', response);
            close(selectedNodes);
        }, function cancel(response) {
            logger.error('Error updating Tor exit nodes ', response);
            vm.errorMsg = response.statusText;
            NotificationService.error('ADMINCONSOLE.DIALOG.EDIT_TOR_COUNTRIES.NOTIFICATION.SAVE_ERROR', response);
        });
    }

    //Select all exit node countries
    function selectAllExitNodes() {
        vm.filteredTorCountries.forEach((country) => {
            country._checked = true;
        });
    }

    //Deselect all tor exit nodes -> random exit node
    function deselectAllExitNodes() {
        vm.filteredTorCountries.forEach((country) => {
            country._checked = false;
        });
    }

    function close(param) {
        $mdDialog.hide(param);
    }

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.$onInit = function() {
        vm.torCountries = setTorCountries(countryList, selectedList);
        vm.filteredTorCountries = angular.copy(vm.torCountries);

        vm.searchProps = ['name'];
        vm.searchTerm = '';

        vm.selectedTorCountries = angular.copy(selectedList);
    };

}
