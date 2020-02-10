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
//FIXME: This method is now only used to retrieve the timezone regions and there related cities.
// perhaps it would be justified to move the code to the SettingsService?
export default function TimezoneService(logger, $http, $q) {
    'ngInject';

    const PATH = '/api/adminconsole/timezone/';
    const PATH_GET_REGIONS = PATH + 'continents';
    const PATH_SET_REGION_GET_CITIES = PATH + 'continent/countries';

    function getRegions() {
        return $http.get(PATH_GET_REGIONS).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error getting regions ', response);
            return $q.reject(response);
        });
    }

    //FIXME: This method should be renamed to getCitiesForRegion() or so.
    // and the API method should be changed from PUT to GET, as it does not change the server state.
    function setRegionAndGetCities(region) {
        const config = {
            timezoneContinent: region
        };
        return $http.put(PATH_SET_REGION_GET_CITIES, config).then(function success(response) {
            return response;
        }, function error(response) {
            logger.error('Error setting regions ', response);
            return $q.reject(response);
        });
    }

    return {
        getRegions: getRegions,
        setRegionAndGetCities: setRegionAndGetCities
    };
}
