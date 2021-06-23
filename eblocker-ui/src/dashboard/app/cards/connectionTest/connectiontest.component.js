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
export default {
    templateUrl: 'app/cards/connectionTest/connectiontest.component.html',
    controller: ConnectionTestController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function ConnectionTestController($timeout, $q, $location, ConnectionTestService, DeviceService, DnsService,
                                  NetworkService, SslService, CardService) {
    'ngInject';

    const vm = this;
    const minimumTestDuration = 2000;
    const CARD_NAME = 'CONNECTION_TEST';
    /*
     * Variables
     */

    // General information
    
    // If the device the user is currently using is active or not
    vm.currentDeviceActivated = false;
    vm.checksStatus = {running: false, resultsPresent: false};
    // To switch between "check" and "check again"
    vm.firstCheck = true;

    // Now about the checks
    // Results of the check
    vm.checkResults = {};
    vm.checkResultsUi = {};
    vm.showExplanations = {};

    // Functions
    vm.connectionTest = connectionTest;
    vm.clear = clear;

    vm.$onInit = function() {
        hideAllExplanations();
        DeviceService.getDevice().then(function success(response) {
            vm.currentDeviceActivated = response.data.enabled;
        }, function error() {
            vm.currentDeviceActivated = false;
        });
        setHttpLocation();
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function hideAllExplanations() {
        vm.checkResultsUi = {};
        vm.showExplanations = {};
    }

    function setHttpLocation() {
        if ($location.protocol() === 'https') {
            vm.dashboardLoadedViaHttps = true;
            vm.dashboardHttpLocation = 'http://' + $location.host() + '/dashboard/';
        } else {
            vm.dashboardLoadedViaHttps = false;
        }
    }

    function connectionTest() {
        hideAllExplanations();
        vm.firstCheck = false;
        vm.checksStatus.running = true;

        var cfgData = {};
        getConfigurationData().then(function(dicConfigData) {
            cfgData = dicConfigData;
            console.log(cfgData);
            return ConnectionTestService.makeConnectionTests();
        }, function(reason) {
            console.log('Could not get configuration data for connection tests');
            console.log(reason);
        }).then(function(response) {
            console.log(response);
            vm.checkResults = response;
            vm.checkResultsUi['adsdomain'] = evaluateAdsDomain(cfgData, response.adsDomainBlockerTest);
            vm.checkResultsUi['tracker']   = evaluateTracker(  cfgData, response.trackerDomainBlockerTest);
            vm.checkResultsUi['dns']       = evaluateDns(      cfgData, response.dnsFirewallTest);
            vm.checkResultsUi['http']      = evaluateHttp(     cfgData, response.httpRoutingTest);
            vm.checkResultsUi['https']     = evaluateHttps(    cfgData, response.httpsRoutingTest);
            vm.checkResultsUi['pattern']   = evaluatePattern(  cfgData, response.patternBlockerTest);
            vm.checkResultsUi['routing']   = evaluateRouting(  cfgData, response.routingTest);
            vm.checkResultsUi['domainblocker'] = combinedDomainBlockerResult(vm.checkResultsUi['adsdomain'],
                                                                             vm.checkResultsUi['tracker']);
            vm.checkResultsUi['webfiltering'] = combinedWebFilteringResult(vm.checkResultsUi['http'],
                                                                           vm.checkResultsUi['https']);
            vm.checksStatus.resultsPresent = true;
            vm.checksStatus.running = false;
            console.log(vm.checkResultsUi);
        }, function error(response) {
            console.log('Could not perform connection tests');
            console.log(response);
            vm.checkResults = response;
        });
    }

    function getConfigurationData() {
        // First, get required config-data. 
        // It must be present when the test finishes such that the results can be evaluated
        var dicConfigPromises = {};
        var dicConfigData = {};
        //
        // HTTPS Routing
        //
        dicConfigPromises['https'] = SslService.getSslStatus(true).then(
                function success(response){
                    dicConfigData['https'] = response.data;
                }, function error(response){
                    console.log(response);
                });
        //
        // Device Service (adsDomainBlocker, ...)
        //
        dicConfigPromises['device'] = DeviceService.getDevice(true).then(
                function success(response){
                    dicConfigData['device'] = response.data;
                }, function error(response){
                    console.log(response);
                });
        
        //
        // Network service
        //
        dicConfigPromises['network'] = NetworkService.getNetworkStatus().then(
                function success(response){
                    dicConfigData['network'] = response.data;
                }, function error(response) {
                    console.log(response);
                });
        //
        // DNS Service
        //
        dicConfigPromises['dns'] = DnsService.getStatus(true).then(
                function success(response){
                    dicConfigData['dns'] = response.data;
                }, function error(response){
                    console.log(response);
                });

        // Add a minimum delay until the tests are complete:
        dicConfigPromises['timeout'] = $timeout(minimumTestDuration);

        return $q.all(dicConfigPromises).then(function() {
            return dicConfigData;
        }, function(reason) {
            console.log('Failed to get configuration data');
            console.log(reason);
        });
    }

    function evaluateAdsDomain(cfgData, testresult) {
        return evaluateDomainBlocker(cfgData, testresult, 'filterAdsEnabled');
    }

    function evaluateTracker(cfgData, testresult) {
        return evaluateDomainBlocker(cfgData, testresult, 'filterTrackersEnabled');
    }

    function evaluateDomainBlocker(cfgData, testresult, filterTypeEnabledKey) {
        var deviceConfig = cfgData['device'];
        var httpsConfig = cfgData['https'];
        var dnsEnabled = cfgData['dns'];
        if (!deviceConfig || !httpsConfig) {
            return {ok: false, expected: false, expl: ['CONFIGURATION_DATA_MISSING']};
        }
        var shouldBlockDomains = shouldUseDomainBlocker(deviceConfig, httpsConfig) &&
            dnsEnabled && deviceConfig[filterTypeEnabledKey];
        var ok = testresult.checkStatus === 'passed';
        var expected = shouldBlockDomains === ok;
        var expl = [];
        if (!expected) {
            expl.push('DNS_RESPONSE_CACHED');
        }
        return {ok: ok, expected: expected, expl: expl};
    }

    function evaluateDns(cfgData, testresult) {
        var dnsEnabled = cfgData['dns'];
        var ok = testresult.checkStatus === 'passed';
        // Switched on and works or switched off and doesn't work
        var expected = dnsEnabled === ok;
        var expl = [];
        if (!expected) {
            expl.push('DNS_RESPONSE_CACHED');
        }
        return {ok: ok, expected: expected, expl: expl};
    }

    function evaluateHttp(cfgData, testresult) {
        var ok = testresult.checkStatus === 'passed';
        var expected = ok;
        var expl = [];
        if (!expected) {
            expl.push('INTERNET_CONNECTIVITY');
        }
        return {ok: ok, expected: expected, expl: expl};
    }

    function evaluateHttps(cfgData, testresult) {
        var httpsConfig = cfgData['https'];
        if (!httpsConfig) {
            return {ok: false, expected: false, expl: ['CONFIGURATION_DATA_MISSING']};
        }
        var shouldProcessHttps = httpsConfig.deviceSslStatus && httpsConfig.globalSslStatus;
        var ok = testresult.checkStatus === 'passed';
        var expected = shouldProcessHttps === ok;
        var expl = [];
        if (!expected) {
            if (shouldProcessHttps) {
                expl.push('CERT_NOT_INSTALLED');
            }
            expl.push('EXISTING_SSL_SESSION');
        }
        return {ok: ok, expected: expected, expl: expl};
    }

    function evaluatePattern(cfgData, testresult) {
        var deviceConfig = cfgData['device'];
        var httpsConfig = cfgData['https'];
        if (!deviceConfig || !httpsConfig) {
            return {ok: false, expected: false, expl: ['CONFIGURATION_DATA_MISSING']};
        }
        var ok = testresult.checkStatus === 'passed';
        var expected = shouldUsePatternBlocker(deviceConfig, httpsConfig) === ok;
        var expl = [];
        if (!expected) {
            expl.push('INTERNET_CONNECTIVITY');
        }
        return {ok: ok, expected: expected, expl: expl};
    }

    function evaluateRouting(cfgData, testresult) {
        var networkConfig = cfgData['network'];
        if (!networkConfig) {
            return {ok: false, expected: false, expl: ['CONFIGURATION_DATA_MISSING']};
        }
        var ok = testresult.checkStatus === 'passed';
        var expected = ok;
        var expl = [];
        if (!expected) {
            expl.push('ROUTING_SECOND_NETWORK');
            if (!networkConfig.automatic) {
                expl.push('DHCP_LEASE_OLD');
            }
        }
        return {ok: ok, expected: expected, expl: expl};
    }

    function combinedDomainBlockerResult(adsResult, trackerResult) {
        var ok = false;
        var expected = adsResult.expected && trackerResult.expected;
        if (expected) {
            ok = adsResult.ok || trackerResult.ok;
        }
        return {ok: ok, expected: expected};
    }

    function combinedWebFilteringResult(httpResult, httpsResult) {
        var expected = httpResult.expected && httpsResult.expected;
        var ok = expected;
        return {ok: ok, expected: expected};
    }

    function clear() {
        hideAllExplanations();
        vm.checkResults = {};
        vm.checkResultsUi = {};
        vm.checksStatus.running = false;
        vm.firstCheck = true;
        vm.checksStatus.resultsPresent = false;
    }

    function shouldUseDomainBlocker(device, https) {
        if (device.filterMode === 'NONE') {
            return false;
        } else if (device.filterMode === 'AUTOMATIC' &&
                   https.deviceSslStatus === true &&
                   https.globalSslStatus === true) {
            return false;
        } else if (device.filterMode === 'ADVANCED') {
            return false;
        } else {
            return true;
        }
    }

    function shouldUsePatternBlocker(device, https) {
        if (device.filterMode === 'NONE') {
            return false;
        } else if (device.filterMode === 'AUTOMATIC' &&
                   https.deviceSslStatus === true &&
                   https.globalSslStatus === true) {
            return true;
        } else if (device.filterMode === 'ADVANCED') {
            return true;
        } else {
            return false;
        }
    }
}
