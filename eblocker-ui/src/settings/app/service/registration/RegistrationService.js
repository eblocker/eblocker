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
export default function RegistrationService(logger, $http, $q, NotificationService, DataCachingService) {
    'ngInject';
    'use strict';

    const PATH = '/api/adminconsole/registration';
    const config = {'timeout': 3000};

    let productInfo, registrationInfo, cachedRegistration;

    function loadRegistrationInfo(reload) {
        cachedRegistration = DataCachingService.loadCache(cachedRegistration, PATH, reload, config)
            .then(function(response) {
            productInfo = response.data.productInfo || {};
            if (angular.isUndefined(productInfo.productFeatures)) {
                productInfo.productFeatures = [];
            }
            registrationInfo = setRegistrationData(response.data);
            return response;
        }, function(response) {
            logger.error('Getting product info from eBlocker failed with status ' +
                response.status + ' - ' + response.data);
            return $q.reject(response.data);
        });
        return cachedRegistration;
    }

    function invalidateCache() {
        cachedRegistration = undefined;
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
        registrationInfo.postRegistrationInformation = registrationData.postRegistrationInformation;

        return registrationInfo;
    }

    function register(registrationUserData) {
        return $http.post(PATH, registrationUserData).then(function success(response) {
            if (!response.data.needsConfirmation) {
                return setRegistrationData(response.data);
            }
            logger.info('User confirmation is required to proceed with registration.');
            return response;
        }, function error(response) {
            logger.info('status code received while registering: ' + response.status);
            let errorKey = response.data.toUpperCase().replace(/\./g, '_');
            if (response.status === 400) {
                // Expected errors will be shown and handled in the wizard/form
                // No need for additional toast.
                if (errorKey === undefined || errorKey === '') {
                    errorKey = 'FILL_IN_ALL_FIELDS';
                }
            } else {
                // Unexpected error should show additional toast to make clear that something is really wrong.
                NotificationService.error('ADMINCONSOLE.SERVICE.REGISTRATION.ERROR_REGISTRATION_NOT_POSSIBLE',
                    response);
                errorKey = 'REGISTRATION_NOT_POSSIBLE';
            }
            return $q.reject(errorKey);
        }).finally(function() {
            invalidateCache();
        });
    }

    /**
     *
     * @param key FAM, PRO, BAS or WOL
     */
    function hasProductKey(key) {
        // when not registered, we need to specifically check for 'WOL'
        return key === 'WOL' || productInfo.productFeatures.indexOf(key) > -1;
    }

    function isEvaluationLicense() {
        if (!angular.isDefined(productInfo)) {
            return false;
        }
        if (!angular.isDefined(productInfo.productFeatures)) {
            return false;
        }
        var features = productInfo.productFeatures;
        for (var i = 0; i < features.length; i++) {
            if (features[i].substring(0, 4) === 'EVL_') {
                return true;
            }
        }
        return false;
    }

    function getProductInfo() {
        return productInfo;
    }

    function getRegistrationInfo() {
        return registrationInfo;
    }

    function isRegistered() {
        return angular.isDefined(registrationInfo) &&
            angular.isDefined(registrationInfo.isRegistered) &&
            registrationInfo.isRegistered;
    }

    function reset() {
        return $http.delete(PATH).then(function(response){
            invalidateCache();
            return response;
        }, function(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.REGISTRATION.ERROR_RESET', response);
            return $q.reject();
        });
    }

    return {
        register: register,
        getProductInfo: getProductInfo,
        getRegistrationInfo: getRegistrationInfo,
        loadRegistrationInfo: loadRegistrationInfo,
        hasProductKey: hasProductKey,
        isEvaluationLicense: isEvaluationLicense,
        isRegistered: isRegistered,
        reset: reset,
        invalidateCache: invalidateCache
    };
}
