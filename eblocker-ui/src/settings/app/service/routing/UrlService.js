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
export default function UrlService(logger, $state, $localStorage) {
    'ngInject';

    function convertUrlParamsToObject(param) {
        const decoded = decodeURIComponent(param);
        return JSON.parse(decoded);
    }

    function convertObjectToUrlParam(obj) {
        const string = JSON.stringify(obj);
        const encodedString = encodeURIComponent(string);
        return encodedString;
    }

    function getPrintViewUrl(param) {
        if (angular.isUndefined(param.templateUrl)) {
            const msg = 'Please define a \'templateUrl\' property for your param object!';
            logger.error('Error creating print view URL.', msg);
            throw Error(msg);
        }
        let urlRel = $state.href('print', {}, {absolute: false});
        $localStorage.eblockerPrintParam = param;
        return urlRel;
    }

    return {
        getPrintViewUrl: getPrintViewUrl
    };
}
