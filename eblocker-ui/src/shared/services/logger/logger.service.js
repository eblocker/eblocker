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
export default function Logger($log) {
    'ngInject';
    'use strict';

    function error(message, data, title) {
        $log.error('Error: ' + message, angular.isDefined(data) ? data : '');
    }

    function info(message, data, title) {
        // $log.info('Info: ' + message, angular.isDefined(data) ? data : '');
    }

    function debug(message, data, title) {
        // $log.info('Debug: ' + message, angular.isDefined(data) ? data : '');
    }

    function success(message, data, title) {
        $log.info('Success: ' + message, angular.isDefined(data) ? data : '');
    }

    function warning(message, data, title) {
        $log.warn('Warning: ' + message, angular.isDefined(data) ? data : '');
    }

    return {
        showToasts: true,

        error: error,
        info: info,
        debug: debug,
        success: success,
        warning: warning,
        warn: warning,

        log: $log.log
    };
}
