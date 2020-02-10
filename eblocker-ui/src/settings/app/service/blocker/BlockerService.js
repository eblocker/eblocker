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
export default function BlockerService(logger, $http, $q, $filter, DataCachingService, BLOCKER_TYPE) {
    'ngInject';

    const PATH = '/api/blockers/';

    const BLOCKER_FORMAT = [
        {key: 'EASYLIST', availableTypeList: [BLOCKER_TYPE.PATTERN]},
        {key: 'URLS', availableTypeList: [BLOCKER_TYPE.PATTERN]},
        {key: 'DOMAINS', availableTypeList: [BLOCKER_TYPE.DOMAIN]},
        {key: 'SQUID_ACL', availableTypeList: [BLOCKER_TYPE.DOMAIN]},
        {key: 'ETC_HOSTS', availableTypeList: [BLOCKER_TYPE.DOMAIN]}
    ];

    let blockerCache;

    function getBlockers(reload, config) {
        blockerCache = DataCachingService.loadCache(blockerCache, PATH, reload, config);
        return blockerCache;
    }

    function getBlockerById(id) {
        return $http.get(PATH + id);
    }

    function createBlocker(blocker) {
        return $http.post(PATH, blocker);
    }

    function updateBlocker(blocker) {
        return $http.put(PATH + blocker.id, blocker);
    }

    function deleteBlocker(id) {
        return $http.delete(PATH + id);
    }

    function getBlockersByTypeCategory(blockerList, type, category) {
        return $filter('filter')(blockerList, (blocker) => {
            return blocker.type === type && blocker.category === category;
        });
    }

    function getFormatList(type) {
        return $filter('filter')(BLOCKER_FORMAT, (format) => {
            return format.availableTypeList.indexOf(type) > -1;
        });
    }

    return {
        getBlockers: getBlockers,
        getBlockerById: getBlockerById,
        createBlocker: createBlocker,
        updateBlocker: updateBlocker,
        deleteBlocker: deleteBlocker,
        getBlockersByTypeCategory: getBlockersByTypeCategory,
        getFormatList: getFormatList
    };
}
