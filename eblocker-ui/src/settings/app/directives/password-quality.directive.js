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
export default function PasswordQualityDirective() {
    'ngInject';

    return {
        replace: false,
        restrict: 'EACM',
        link: function (scope, element, attributes) {

            let getQuality = function (password) {

                let normalized = password.
                split('').
                filter(function(token, i, array) {
                    return array.indexOf(token) === i;
                }).
                join('');

                let specialChars = /[$-/:-?{-~!"^_`\[\]]/g;

                let charClasses = 1;
                charClasses += /[a-z]+/.test(normalized) ? 1 : 0;
                charClasses += /[A-Z]+/.test(normalized) ? 1 : 0;
                charClasses += /[0-9]+/.test(normalized) ? 1 : 0;
                charClasses += specialChars.test(normalized) ? 1 : 0;


                return normalized.length * charClasses * 5 / 2;

            };

            let getDisplayMode = function (quality) {

                if (quality <= 20) {
                    return { 'q': 1, 'c': '#ff0000'};

                } else if (quality <= 40) {
                    return { 'q': 2, 'c': '#ff0000'};

                } else if (quality <= 60) {
                    return { 'q': 3, 'c': '#ff9900'};

                } else if (quality <= 80) {
                    return { 'q': 4, 'c': '#ffff00'};

                } else {
                    return { 'q': 5, 'c': '#00cc00'};

                }
            };

            const watcher = scope.$watch(attributes.passwordQuality, function (newValue) {
                if (!angular.isDefined(newValue) || newValue === '') {
                    element.css('display', 'none');
                } else {
                    let quality = getQuality(newValue);
                    let mode = getDisplayMode(quality);
                    let points = element.children();
                    element.css('display', 'inline');
                    for (let i = 0; i < points.length; i++) {
                        let point = points[i];
                        point.style.background = i < mode.q ? mode.c : '#DDD';
                    }
                }
            });
            scope.$on('$destroy', function() {
                if (angular.isDefined(watcher)) {
                    watcher();
                }
            });
        },
        templateUrl: 'app/directives/password-quality.directive.html'
    };
}
