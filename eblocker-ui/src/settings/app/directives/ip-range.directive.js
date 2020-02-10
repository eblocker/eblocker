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
export default function IpRangeDirective() {
    'ngInject';

    return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope, element, attr, ctrl){
            ctrl.$validators.ipRange = function(modelValue, viewValue) {

                const isIpRange = function(ipaddress){
                    return (/^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)){3}(\/([0-9]|[1-2][0-9]|3[0-2]))?$/.test(ipaddress)); // jshint ignore: line
                };

                if (!angular.isDefined(viewValue)) {
                    return true;
                }

                // the actual verification
                const lines = viewValue.split('\n');
                for (let i = 0; i < lines.length; i++){
                    const line = lines[i].trim();
                    if (line !== '' && !isIpRange(line)){
                        return false;
                    }
                }
                return true;
            };
        }
    };
}
