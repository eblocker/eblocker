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
export default function UniqueNameDirective($q) {
    'ngInject';

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

                let def = $q.defer();

                let regex = /^(?:https?:\/\/)?(?:[^@\/\n]+@)?([^:\/\n]+)/g;

                let lines = viewValue.split('\n');
                for (let lineIndex in lines) { // jshint ignore: line
                    // reset regular expression
                    regex.lastIndex = 0;
                    let line = lines[lineIndex].trim();
                    // empty lines are ignored
                    if (line === '') {
                        continue;
                    }
                    // see if it is a domain
                    let arr = regex.exec(line);
                    let domain = arr[1];
                    // not allowed to begin with '.' or '-'
                    if (domain.startsWith('.') || domain.startsWith('-')) {
                        return false;
                    }
                    // must be long enough
                    let parts = domain.split('.');
                    if (parts.length < 2) {
                        return false;
                    }
                    // shall not have consecutive periods
                    for (let part in parts) {
                        if (parts[part] === '') {
                            return false;
                        }
                    }
                }
                return true;
            };
        }
    };
}
