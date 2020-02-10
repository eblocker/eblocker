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
export default function CloakingService($http, $q) {
    'ngInject';

    const PATH = '/api/adminconsole/useragent/';
    const PATH_CLOAKED = PATH + 'cloaked';
    const PATH_LIST = PATH + 'list';

    function getCloakedUserAgentByDeviceId(deviceId, assignedUserId) {
        return $http.get(PATH_CLOAKED + '?deviceId=' + deviceId + '&userId=' + assignedUserId).
        then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function setCloakedUserAgentByDeviceId(cloaked) {
        return $http.put(PATH_CLOAKED, cloaked).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getUserAgents() {
        return $http.get(PATH_LIST).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    return {
        getCloakedUserAgentByDeviceId: getCloakedUserAgentByDeviceId,
        setCloakedUserAgentByDeviceId: setCloakedUserAgentByDeviceId,
        getUserAgents: getUserAgents
    };
}
