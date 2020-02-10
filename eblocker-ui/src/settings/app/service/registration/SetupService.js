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
export default function SetupService($http, DataCachingService, $sce) {
    'ngInject';

    const PATH = '/api/adminconsole/setup/';
    const PATH_CHECK_SERIAL = PATH + 'serial/checkformat';

    let infoCache;

    function getInfo(reload) {
        infoCache = DataCachingService.loadCache(infoCache, PATH + 'info', reload);
        return infoCache;
    }

    function checkSerialNumber(serialNumber) {
        return $http.put(PATH_CHECK_SERIAL, {deviceSerialNumber: serialNumber});
    }

    function getTos(langKey) {
        const tos = {};
        return getInfo().then(function success(response) {
            const setupInfo = response.data;
            const tosContainer = angular.isObject(setupInfo.tosContainer) ? setupInfo.tosContainer : null;
            if (angular.isObject(tosContainer)) {
                tos.licenseText = angular.isObject(tosContainer.text) ? $sce.trustAsHtml(tosContainer.text[langKey]) :
                    undefined;
                tos.licenseDate = angular.isString(tosContainer.date) ? Date.parse(tosContainer.date) : undefined;
                tos.licenseVersion = angular.isString(tosContainer.version) ? tosContainer.version : undefined;
            }
            return tos;
        });
    }

    function getTosHtml(langKey) {
        const tos = {};
        return getInfo().then(function success(response) {
            const setupInfo = response.data;
            const tosContainer = angular.isObject(setupInfo.tosContainer) ? setupInfo.tosContainer : null;
            if (angular.isObject(tosContainer)) {
                tos.licenseText = angular.isObject(tosContainer.text) ? tosContainer.text[langKey] : undefined;
                tos.licenseDate = angular.isString(tosContainer.date) ? Date.parse(tosContainer.date) : undefined;
                tos.licenseVersion = angular.isString(tosContainer.version) ? tosContainer.version : undefined;
            }
            return tos;
        });
    }

    let hasSetupWizardBeenExecuted = false;
    function hasSetupBeenExecuted(bool) {
        if (angular.isDefined(bool)) {
            hasSetupWizardBeenExecuted = bool;
        }
        return hasSetupWizardBeenExecuted;
    }

    return {
        getInfo: getInfo,
        checkSerialNumber: checkSerialNumber,
        getTos: getTos,
        getTosHtml: getTosHtml,
        hasSetupBeenExecuted: hasSetupBeenExecuted
    };
}
