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
export default function PasswordService(logger, $http, $q, NotificationService) {
    'ngInject';

    const PATH = '/api/adminconsole/authentication/';

    function enable(credentials) {
        return $http.post(PATH + 'enable', credentials).then(function success(response) {
            return response;
        }, function error(response) {
            if (response.status === 401) {
                return $q.reject('passwordInvalid');

            } else {
                logger.error('Setting/changing password failed with status ' +
                    response.status + ' - ' + response.data);
                NotificationService.error('ADMINCONSOLE.PASSWORD_SERVICE.NOTIFICATION.ERROR_SERVICE_PASSWORD_ENABLE',
                    response);
            }
        });
    }

    function disable(credentials) {
        return $http.post(PATH + 'disable', credentials).then(function success(response) {
            return response;
        }, function error(response) {
            if (response.status === 401) {
                return $q.reject('passwordInvalid');

            } else {
                logger.error('Removing password failed with status ' +
                    response.status + ' - ' + response.data);
                NotificationService.error('ADMINCONSOLE.PASSWORD_SERVICE.NOTIFICATION.ERROR_SERVICE_PASSWORD_DISABLE',
                    response);
            }
        });
    }

    function initiateReset() {
        return $http.post(PATH + 'initiateReset', {}).then(function success(response) {
            return response.data;
        }, function error(response) {
            logger.info('Initiating password reset failed with status ' + response.status + ' - ' + response.data);
            let errorKey = 'ADMINCONSOLE.PASSWORD_SERVICE.NOTIFICATION.ERROR_SERVICE_PASSWORD_INITIATE_RESET';
            if (angular.isDefined(response.data)) {
                errorKey = response.data.toUpperCase().replace(/\./g, '_');
            }
            return $q.reject(errorKey);
        });
    }

    function executeReset(resetToken) {
        return $http.post(PATH + 'executeReset', { 'resetToken': resetToken }).then(function success(response) {
            // nothing to do here
        }, function error(response) {
            logger.error('Executing password reset failed with status ' + response.status + ' - ' + response.data);
            //notificationService.error('ERROR_SERVICE_PASSWORD_EXECUTE_RESET', response);
            let errorKey = 'ADMINCONSOLE.PASSWORD_SERVICE.NOTIFICATION.ERROR_SERVICE_PASSWORD_EXECUTE_RESET';
            if (angular.isDefined(response.data)) {
                errorKey = response.data.toUpperCase().replace(/\./g, '_');
            }
            return $q.reject(errorKey);
        });
    }

    function cancelReset(resetToken) {
        return $http.post(PATH + 'cancelReset', { 'resetToken': resetToken }).then(function success(response) {
            // nothing to do here
        }, function error(response) {
            logger.error('Cancelling password reset failed with status ' + response.status + ' - ' + response.data);
            //notificationService.error('ERROR_SERVICE_PASSWORD_CANCEL_RESET', response);
            let errorKey = 'ADMINCONSOLE.PASSWORD_SERVICE.NOTIFICATION.ERROR_SERVICE_PASSWORD_CANCEL_RESET';
            if (angular.isDefined(response.data)) {
                errorKey = response.data.toUpperCase().replace(/\./g, '_');
            }
            return $q.reject(errorKey);
        });
    }

    return {
        enable: enable,
        disable: disable,
        initiateReset: initiateReset,
        executeReset: executeReset,
        cancelReset: cancelReset
    };
}
