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
import SecurityService from './security.service.js';
import LocalTimestampService from '../devices/localTimestamp.service';
import OnlineTime from '../devices/onlineTime.service';
import IconService from '../devices/icon.service';
import SslService from '../devices/ssl.service';
import registration from '../devices/registration.service';
import FilterStatistics from '../devices/filterStatistics.service';

(function() {
    'use strict';

    angular.module('eblocker.dashboard.security', [])
        .factory('security', SecurityService)
        .factory('LocalTimestampService', LocalTimestampService)
        .factory('onlineTime', OnlineTime)
        .factory('iconService', IconService)
        .factory('SslService', SslService)
        .factory('registration', registration)
        .factory('filterStatistics', FilterStatistics);
})();
