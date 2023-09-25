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
export default function UniqueNameDirective($q, IpUtilsService, DomainUtilsService) {
    'ngInject';

    function isValidDomainOrIp(str) {
        // Domains are accepted
        if (DomainUtilsService.isDomain(str)) {
            return true;
        }
        // IP addresses or ranges in CIDR notation are accepted
        if (IpUtilsService.isIpAddressOrRange(str)) {
            return true;
        }
        if (str.startsWith('http://') || str.startsWith('https://')) {
            try {
                let url = new URL(str);
                if (DomainUtilsService.isDomain(url.hostname) || IpUtilsService.isIpAddress(url.hostname)) {
                    return true;
                }
            } catch (e) { // URL parsing failed
                return false;
            }
        }
        return false;
    }

    return {
        // limit usage to argument only
        restrict: 'A',
        // require NgModelController, i.e. require a controller of ngModel directive
        require: 'ngModel',
        // create linking function and pass in our NgModelController as a 4th argument
        link: function(scope, element, attr, ctrl) {

            ctrl.$validators.urlDomains = function(modelValue, viewValue) { // jshint ignore: line
                if (ctrl.$isEmpty(modelValue)) {
                    // consider empty models to be valid
                    return $q.when();
                }

                let lines = viewValue.split('\n');
                for (let i = 0; i < lines.length; i++) {
                    let line = lines[i].trim();
                    // empty lines are ignored
                    if (line === '') {
                        continue;
                    }
                    if (!isValidDomainOrIp(line)) {
                        return false;
                    }
                }
                return true;
            };
        }
    };
}
