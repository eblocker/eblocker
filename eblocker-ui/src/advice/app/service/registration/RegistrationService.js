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
export default function RegistrationService(logger, $http, $q) {
    'ngInject';
    'use strict';

    let PATH = '/api/advice/registration';
    let config = {'timeout': 3000};

    let productInfo, registrationInfo;

    function getProductInfo() {
        return $http.get(PATH, config).then(function(response) {
            productInfo = response.data.productInfo || {};
            if (angular.isUndefined(productInfo.productFeatures)) {
                productInfo.productFeatures = [];
            }
            registrationInfo = setRegistrationData(response.data);
            return productInfo;
        }, function(response) {
            logger.error('Getting product info from eBlocker failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
    }

    function setRegistrationData(registrationData) {
        const now = new Date();
        const registrationInfo = {};
        registrationInfo.deviceId = registrationData.deviceId;
        registrationInfo.deviceName = registrationData.deviceName;
        registrationInfo.registrationState = registrationData.registrationState;
        registrationInfo.licenseType = registrationData.licenseType;
        registrationInfo.deviceRegisteredBy = registrationData.deviceRegisteredBy;
        registrationInfo.productInfo = registrationData.productInfo;

        if (angular.isDefined(registrationData.deviceRegisteredAt)) {
            registrationInfo.deviceRegisteredAt = new Date(registrationData.deviceRegisteredAt);
        } else {
            registrationInfo.deviceRegisteredAt = undefined;
        }
        if (angular.isDefined(registrationData.licenseNotValidAfter)) {
            registrationInfo.licenseNotValidAfter = new Date(registrationData.licenseNotValidAfter);
            registrationInfo.licenseExpired = registrationInfo.licenseNotValidAfter < now;
        } else {
            registrationInfo.licenseNotValidAfter = undefined;
        }

        registrationInfo.isRegistered = registrationData.registrationState === 'OK' ||
            registrationData.registrationState === 'OK_UNREGISTERED';

        registrationInfo.licenseLifetime = registrationData.licenseLifetime;
        registrationInfo.licenseAboutToExpire = registrationData.licenseAboutToExpire;

        return registrationInfo;
    }

    /**
     *
     * @param key FAM, PRO, BAS or WOL
     * Case WOL: return true, in case WOL is not (yet) in database
     */
    function hasProductKey(key) {
        return key === 'WOL' ? true : productInfo.productFeatures.indexOf(key) > -1;
    }

    function getRegistrationInfo() {
        return registrationInfo;
    }

    return {
        getProductInfo: getProductInfo,
        getRegistrationInfo: getRegistrationInfo,
        hasProductKey: hasProductKey
    };
}
