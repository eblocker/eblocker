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
export default function UserAgentService($http, logger, $q) {
    'ngInject';

    let PATH = '/api/useragent';

    function getUserAgentList() {
        return $http.get(PATH + '/list').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get user agent list ', response);
            $q.reject(response);
        });
    }

    function setUserAgent(name, value) {
        return $http.put(PATH + '/cloaked', {
            userAgentName: name,
            userAgentValue: value
        }).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to set user agent (name: ' + name + ' / value: ' + value + ') ', response);
            $q.reject(response);
        });
    }

    function getCloakedUserAgentForDevice() {
        return $http.get(PATH + '/cloaked').then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Unable to get cloaked user agent ', response);
            $q.reject(response);
        });
    }

    return {
        'getUserAgentList': getUserAgentList,
        'setUserAgent': setUserAgent,
        'getCloakedUserAgentForDevice': getCloakedUserAgentForDevice
    };
}
