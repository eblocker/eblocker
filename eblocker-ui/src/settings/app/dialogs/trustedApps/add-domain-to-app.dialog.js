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
export default function AddDomainToAppController(TrustedAppsService, $mdDialog, appModules, suggestions,
                                                 selectedDomains) {
    'ngInject';

    const vm = this;
    vm.suggestions = suggestions;
    vm.appModules = appModules
        .filter(function(module) {
            return !module.hidden;
        })
        .sort(function(a, b) {
            let la = a.name.toLowerCase();
            let lb = b.name.toLowerCase();
            return la < lb ? -1 : la > lb ? 1 : 0;
        });

    function getSelectedDomainsIps(connections) {
        return connections.map(function(connection) {
            return connection.domainIp;
        }).join('\n');
    }

    let domainsIps = getSelectedDomainsIps(selectedDomains);

    vm.input = {
        selectedModule: vm.appModules[0],
        domainsIps: domainsIps
    };

    vm.hide = function() {
        $mdDialog.hide();
    };

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.save = function() {
        let module = vm.input.selectedModule;
        vm.input.selectedModule.whitelistedDomainsIps = vm.input.selectedModule.whitelistedDomainsIps
            .concat(vm.input.domainsIps.split('\n'));

        TrustedAppsService.save(module).then(function success(response) {
            const savedModule = response.data;
            savedModule.enabled = true;
            TrustedAppsService.toggleAppModule(savedModule);
            // updateDomainSuggestions(savedModule.whitelistedDomains, savedModule.name);
            $mdDialog.hide(savedModule);
        });
    };
}
