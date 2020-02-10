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
export default function DnsRecordAddEditController(logger, $q, $mdDialog, record, isNotUnique, save,
                                                   ip6FeatureEnabled) {
    'ngInject';

    const vm = this;
    vm.ip6FeatureEnabled = ip6FeatureEnabled;
    vm.current = record;
    vm.working = {name: record.name, ipAddress: record.ipAddress, ip6Address: record.ip6Address};

    vm.new = !angular.isDefined(record.name);

    vm.isUnique = function(name) {
        const unique = name === vm.current.name || !isNotUnique(name);
        if (unique) {
            return $q.when();
        } else {
            return $q.reject();
        }
    };

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.save = function() {
        save(vm.working, vm.current).then(function(response) {
            if (vm.new) {
                $mdDialog.hide(response);
            } else {
                // ** we do not have an ID, just name and IP and both can be edited.
                // So unless we return the new object, there is not way to update the display values
                // within the edit dialog, because we don't now which object to display (name and ip may have changed)
                $mdDialog.hide(vm.working);
            }
        }, function(response){
            logger.error('Cannot save DNS entry ', response);
        });
    };
}
