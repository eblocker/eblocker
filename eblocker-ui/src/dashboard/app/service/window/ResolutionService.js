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
export default function ResolutionService($window) {
    'ngInject';
    'use strict';

    function getScreenSize() {
        const windowInnerWidth = $window.innerWidth;
        let screenRes;
        if (windowInnerWidth < 550) {
            // Definition for TINY screens: use one column
            screenRes = 'xs';
        } else if (windowInnerWidth < 680) {
            // Definition for SMALL screens: use one column
            screenRes = 'sm';
        } else if (windowInnerWidth < 960) {
            // Definition for MEDIUM SMALL screens: use one column
            // --> to center the cards better for smaller sizes
            screenRes = 'mdsm';
        } else if (windowInnerWidth < 1280) {
            // Definition for MEDIUM screens: use two columns
            screenRes = 'md';
        } else {
            // else if ($window.innerWidth >= 1280) {
            // Definition for LARGE screens: use three columns
            screenRes = 'lg';
        }
        return screenRes;
    }

    return {
        getScreenSize: getScreenSize
    };
}
