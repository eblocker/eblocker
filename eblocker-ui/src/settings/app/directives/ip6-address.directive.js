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
export default function Ip6AddressDirective() {
    'ngInject';

    return {
        restrict: 'A',
        require: 'ngModel',
        link: function(scope, element, attr, ctrl) {
            const FIELD_REGEX = /^[0-9a-fA-F]{1,4}$/;

            ctrl.$validators.ip6Address = function(modelValue, viewValue) {
                if (ctrl.$isEmpty(modelValue)) {
                    return true;
                }

                // at most one placeholder is allowed
                const parts = viewValue.split('::');
                if (parts.length > 2) {
                    return false;
                }
                const placeholder = parts.length === 2;

                // max 8 fields are allowed
                const split = function(value) {
                    if (value.length === 0) {
                        return [];
                    }
                    return value.split(':');
                };
                const fieldsA = split(parts[0]);
                const fieldsB = placeholder ? split(parts[1]) : [];
                if (fieldsA.length + fieldsB.length > 8 || placeholder && fieldsA.length + fieldsB.length === 8) {
                    return false;
                }

                const validateHexFields = function(fields) {
                    for(let i = 0; i < fields.length; ++i) {
                        if (!FIELD_REGEX.test(fields[i])) {
                            return false;
                        }
                    }
                    return true;
                };
                return validateHexFields(fieldsA) && validateHexFields(fieldsB);
            };
        }
    };
}
