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
export default function ConnectionTestService($http, $q) {
    'ngInject';
    const connectionTimeout = 10000; // Milliseconds TODO: Adjust!

    function makeTestRequest(protocol, url) {
        const deferred = $q.defer();

        if (protocol !== 'http' && protocol !== 'https') {
            deferred.reject('Unsupported protocol specified, expected \'http\' or \'https\'');
            return deferred.promise;
        }

        return $http({
            method: 'GET',
            url: protocol + '://' + url,
            timeout: connectionTimeout,
            headers: {'Authorization': undefined, 'Accept': 'application/json'}
        }).then(function success(response) {
            var result = {
                    content: response.data,
                    status: 'success',
                    httpcode: response.status,
                    httpdesc: response.statusText
            };
            deferred.resolve(result);
            return deferred.promise;
        }, function error(response) {
            var result = {
                    content: response.data,
                    status: 'error',
                    httpcode: response.status,
                    httpdesc: response.statusText
            };
            deferred.resolve(result);
            return deferred.promise;
        });
    }

    function makePatternBlockerTestRequest() {
        return makeTestRequest('http', 'setup.eblocker.org/_check_/pattern-blocker').then(
                getResponseProcessor('patternBlockerTest', 204),
                getResponseProcessor('patternBlockerTest', 204));
    }

    function makeHttpRoutingTestRequest(protocol) {
        return makeTestRequest(protocol, 'setup.eblocker.org/_check_/routing').then(
                getResponseProcessor(protocol === 'https' ? 'httpsRoutingTest' : 'httpRoutingTest', 204),
                getResponseProcessor(protocol === 'https' ? 'httpsRoutingTest' : 'httpRoutingTest', 204));
    }

    function makeAdsDomainBlockerTestRequest() {
        return makeTestRequest('http', 'ads.domainblockercheck.eblocker.org/_check_/domain-blocker').then(
                getResponseProcessor('adsDomainBlockerTest', 200),
                getResponseProcessor('adsDomainBlockerTest', 200));
    }

    function makeTrackerDomainBlockerTestRequest() {
        return makeTestRequest('http', 'tracker.domainblockercheck.eblocker.org/_check_/domain-blocker').then(
                getResponseProcessor('trackerDomainBlockerTest', 200),
                getResponseProcessor('trackerDomainBlockerTest', 200));
    }

    function makeDnsFirewallTestRequest() {
        return makeTestRequest('http', 'dnscheck.eblocker.org/api/check/route').then(
                getResponseProcessor('dnsFirewallTest', 204),
                getResponseProcessor('dnsFirewallTest', 204));
    }

    function makeRoutingTestRequest() {
        return makeTestRequest('http', 'controlbar.eblocker.org/api/check/route').then(
                getResponseProcessor('routingTest', 204),
                getResponseProcessor('routingTest', 204));
    }

    function getResponseProcessor(checkType, successHttpCode) {
        return function processResponse(response) {
                return {
                    checkType: checkType,
                    checkStatus: response.httpcode === successHttpCode ? 'passed' : 'failed',
                    checkDetails: response
                };
            };
    }

    function makeConnectionTests() {
        var testresults = {};
        function addToTestResults(response) {
            testresults[response.checkType] = response;
        }
        var promises = [];
        // Execute all makeXTextRequest and collect their results in 'testresults'
        var pPatternBlockerTest = makePatternBlockerTestRequest().then(
                addToTestResults, addToTestResults);
        promises.push(pPatternBlockerTest);

        var pHttpRoutingTest = makeHttpRoutingTestRequest('http').then(
                addToTestResults, addToTestResults);
        promises.push(pHttpRoutingTest);

        var pHttpsRoutingTest = makeHttpRoutingTestRequest('https').then(
                addToTestResults, addToTestResults);
        promises.push(pHttpsRoutingTest);

        var pAdsDomainBlockerTest = makeAdsDomainBlockerTestRequest().then(
                addToTestResults, addToTestResults);
        promises.push(pAdsDomainBlockerTest);

        var pTrackerDomainBlockerTest = makeTrackerDomainBlockerTestRequest().then(
                addToTestResults, addToTestResults);
        promises.push(pTrackerDomainBlockerTest);

        var pRoutingTest = makeRoutingTestRequest().then(
                addToTestResults, addToTestResults);
        promises.push(pRoutingTest);

        var pDnsFirewallTest = makeDnsFirewallTestRequest().then(
                addToTestResults, addToTestResults);
        promises.push(pDnsFirewallTest);

        // All promises are finished (or failed)
        return $q.all(promises).then(function success(){
            return testresults;
        }, function error(){
            return $q.reject(testresults);
        });
    }

    return {
        makeConnectionTests: makeConnectionTests,
        makePatternBlockerTestRequest: makePatternBlockerTestRequest,
        makeHttpRoutingTestRequest: makeHttpRoutingTestRequest,
        makeAdsDomainBlockerTestRequest: makeAdsDomainBlockerTestRequest,
        makeTrackerDomainBlockerTestRequest: makeTrackerDomainBlockerTestRequest,
        makeDnsFirewallTestRequest: makeDnsFirewallTestRequest,
        makeRoutingTestRequest: makeRoutingTestRequest,
        makeTestRequest: makeTestRequest
    };
}
