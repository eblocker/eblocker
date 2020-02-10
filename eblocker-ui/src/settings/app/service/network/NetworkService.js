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
export default function NetworkService($http, $q, $translate, moment) {
    'ngInject';

    const PATH = '/api/adminconsole/network';
    const PATH_DHCP_STATE = PATH + '/dhcpstate';
    const PATH_SETUP_INFO = PATH + '/setupPageInfo';
    const PATH_DHCP_SERVERS = PATH + '/dhcpservers';

    // For the request to /dhcpservers the timeout must be larger than
    // network.unix.dhcp.discovery.timeout (in configuration.properties)
    const REQUEST_TIMEOUT_DHCP_SERVERS = 20000;

    function getDhcpServers() {
        const config = {'timeout': REQUEST_TIMEOUT_DHCP_SERVERS};
        return $http.get(PATH_DHCP_SERVERS, config).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getDhcpState() {
        return $http.get(PATH_DHCP_STATE).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getSetupInfo() {
        return $http.get(PATH_SETUP_INFO).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function getNetworkConfig() {
        return $http.get(PATH).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function setNetworkConfig(config) {
        return $http.put(PATH, config).then(function success(response) {
            return response;
        }, function error(response) {
            return $q.reject(response);
        });
    }

    function processBackendErrors(errors){
        const errorPrefix ='error.network.';
        const ret = {};
        if(angular.isArray(errors)){
            errors.forEach((element) => {
                if(element === errorPrefix + 'ipAddress.wrongNetwork'){
                    ret.ipAddress = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
                else if(element === errorPrefix + 'gateway.wrongNetwork'){
                    ret.gateway = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
                else if(element === errorPrefix + 'dhcpRangeFirst.wrongNetwork'){
                    ret.firstDHCP = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
                else if(element === errorPrefix + 'dhcpRangeLast.wrongNetwork'){
                    ret.lastDHCP = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
                else if(element === errorPrefix + 'dhcp.wrongNetwork'){
                    ret.firstDHCP = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                    ret.lastDHCP = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
                //invalid errors
                else if(element === errorPrefix + 'networkMask.invalid'){
                    ret.networkMask = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
                //other errors
                else if(element === errorPrefix + 'dhcp.invalidRange'){
                    ret.firstDHCP = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                    ret.lastDHCP = 'ADMINCONSOLE.SERVICE.NETWORK.ERROR.IP_ADDRESS_WRONG_NETWORK';
                }
            });
        }
        return ret;
    }

    // Code for the DHCP lease times
    const allowedDhcpLeaseTimes = [600, 1800, 3600, 21600, 86400, 432000, 864000];

    const dhcpLeaseTimes = {};

    Object.keys(allowedDhcpLeaseTimes).forEach((i) => {
        const seconds = allowedDhcpLeaseTimes[i];
        dhcpLeaseTimes[seconds] = constructDhcpLeaseTimeTranslationString(seconds);
    });

    function constructDhcpLeaseTimeTranslationString(secs) {
        return moment.duration(secs, 'seconds').locale($translate.use()).humanize();
    }

    function getDhcpLeaseTimes(){
        return dhcpLeaseTimes;
    }

    function getDhcpLeaseTimeTranslation(leaseTime) {
        let tmpKey = 0;
        Object.keys(dhcpLeaseTimes).forEach((i) => {
            const key = parseInt(i);
            tmpKey = key <= leaseTime ? max(tmpKey, key) : tmpKey;
        });
        return dhcpLeaseTimes[tmpKey];
    }

    function max(a,b) {
        return a < b ? b : a;
    }

    return {
        getDhcpServers: getDhcpServers,
        getDhcpState: getDhcpState,
        getSetupInfo: getSetupInfo,
        getNetworkConfig: getNetworkConfig,
        setNetworkConfig: setNetworkConfig,
        processBackendErrors: processBackendErrors,
        getDhcpLeaseTimes: getDhcpLeaseTimes,
        getDhcpLeaseTimeTranslation: getDhcpLeaseTimeTranslation
    };
}
