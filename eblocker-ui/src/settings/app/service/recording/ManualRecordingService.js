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
export default function ManualRecordingService(logger, $http, $q, NotificationService, $interval) {
    'ngInject';

    const PATH = '/api/adminconsole/recording';
    const PATH_TOGGLE = PATH + '/toggle';
    const PATH_STATUS = PATH + '/status';
    const PATH_RESULT = PATH + '/result';

    function start(recordingContext){
        logger.debug('Start recording');
        return $http.post(PATH_TOGGLE, {
            recordingStatus: 'start',
            targetID: recordingContext.deviceId,
            timeLimit: recordingContext.timeLimit,
            sizeLimit: recordingContext.sizeLimit
        }).then(function() {
            return true; // hopefully started
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.MANUAL_RECORDING.NOTIFICATION.ERROR_START', response);
            return false; // probably not started
        });
    }

    function stop(){
        logger.debug('Stop recording');
        return $http.post(PATH_TOGGLE, {recordingStatus: 'stop'}).then(function() {
            return true; // hopefully stopped
        }, function error(response) {
            NotificationService.error('ADMINCONSOLE.SERVICE.MANUAL_RECORDING.NOTIFICATION.ERROR_STOP', response);
            return false; // probably not stopped
        });
    }

    // Find out which app a domain is whitelisted by
    function domainWhitelistedBy(newUrl, whitelistedDomainsByApps){
        let domain = newUrl.recordedDomain;
        // check if the domain is empty - can happen when only an IP address is known
        if (!angular.isDefined(domain) || domain === ''){
            return;
        }
        do{
            // check if the domain is whitelisted
            if (angular.isDefined(whitelistedDomainsByApps[domain])){
                // store this app, but do not return it. one of its parent domains might also be whitelisted
                newUrl.whitelistedBy.push(whitelistedDomainsByApps[domain]);
                // to save which rule the app is effectively whitelisted by - that is the one closest to the TLD.
                newUrl.selectedDomain = domain;
            }
            // domain not whitelisted, search for parent domain in the next iteration
            let dotpos = domain.search('\\.');
            if (dotpos === -1){
                // no dot remaining means the domain is now only a top-level-domain
                break;
            }
            domain = domain.substring(dotpos + 1, domain.length); // +1 to avoid heading dot
        } while(domain.length > 0);
    }

    // Find out which app an IP is whitelisted by
    function ipWhitelistedBy(newUrl, whitelistedIPsByApps) {
        let ip = newUrl.recordedIp;
        let appList=[];
        // in case just a simple address (no range!) is whitelisted:
        if (angular.isDefined(whitelistedIPsByApps[ip])){
            appList = appList.concat(whitelistedIPsByApps[ip]);
//    		return whitelistedIPsByApps[ip];
        }
        // not a simple address but a range...
        let numip = numericalIP(ip);
        for (let iprange in whitelistedIPsByApps) {
            if (whitelistedIPsByApps.hasOwnProperty(iprange)) {
                if (iprange.search('/') < 0) {
                    // contains no '/' and is therefore no range
                    continue;
                }
                // get ip address and netmask as separate variables
                let rangeIPNumerical = numericalIP(iprange.split('/')[0]);
                let netmask = iprange.split('/')[1];
                // is the ip within the bounds of the range?
                let minIpRangeNum = minIpRangeNumerical(rangeIPNumerical, netmask);
                let maxIpRangeNum = maxIpRangeNumerical(rangeIPNumerical, netmask);
                if (minIpRangeNum <= numip && numip <= maxIpRangeNum) {
                    // recorded entry is whitelisted due to its ip being in an apps range
                    newUrl.flag = 'iprange';
                    // do not return here, there may be different ip ranges
                    // this ip is also a part of
                    appList = appList.concat(whitelistedIPsByApps[iprange]);
                    // since we know the netmask of this range, also remember the netmask
                    // so that it can be displayed in the UI
                    if (netmask < newUrl.iprangemask) {
                        newUrl.iprangemask = netmask;
                    }
                }
                if (appList.length > 0) {
                    newUrl.whitelistedBy = appList;
                }
            }
        }
    }

    function numericalIP(ipaddress) {
        let octetts = ipaddress.split('.'); // adress is a.b.c.d
        let numericalip = parseInt(octetts[0]);                 // contains a
        numericalip = numericalip * 256 + parseInt(octetts[1]); // contains a.b
        numericalip = numericalip * 256 + parseInt(octetts[2]); // contains a.b.c
        numericalip = numericalip * 256 + parseInt(octetts[3]); // contains a.b.c.d
        return numericalip;
    }

    function minIpRangeNumerical(numip, netmask) {
        netmask=parseInt(netmask);
        /*jshint bitwise: false*/
        // bitshift, removing any bits that are 'inside the net', leaving the nets address
        numip = numip >> (32-netmask);
        // filling with 0 from the right results in the lower bound
        numip = numip << (32-netmask);
        return numip;
    }

    function maxIpRangeNumerical(numip, netmask) {
        netmask=parseInt(netmask);
        /*jshint bitwise: false*/
        // bitshift, removing any bits that are 'inside the net', leaving the address of the net
        numip = numip >> (32-netmask);
        for (let i = 0; i < (32-netmask); i++){
            numip = numip * 2 +1; // *2 for left-shift, +1 to set rightmost-bit to 1
        }
        return numip;
    }


    function getResult(recordedUrls, whitelistedDomainsIps){
        return $http.get(PATH_RESULT).then(function(response){ //jshint ignore: line
            const newRecordedUrls = response.data;
            let resultsNew = 0;
            for (let newUrlIndex in newRecordedUrls) {
                if (newRecordedUrls.hasOwnProperty(newUrlIndex)) {
                    const newUrl = newRecordedUrls[newUrlIndex];
                    // flag if new url has a domain
                    if (angular.isDefined(newUrl.recordedDomain) && newUrl.recordedDomain !== '') {
                        newUrl.flag = 'domain';
                    } else {
                        newUrl.flag = 'iprange';
                    }
                    newUrl.iprangemask = 32;
                    let found = false;
                    for (let oldUrlIndex in recordedUrls) {
                        if (recordedUrls.hasOwnProperty(oldUrlIndex)) {
                            const oldUrl = recordedUrls[oldUrlIndex];
                            // also use the ip - not all entries have a domain
                            if (newUrl.flag === 'domain' && oldUrl.recordedDomain === newUrl.recordedDomain ||
                                newUrl.flag !== 'domain' && oldUrl.recordedIp === newUrl.recordedIp) {
                                found = true;
                                // FUTURE: if there are more possible values, advance the following if!
                                //         this may include updating from more values than just 'bump'!
                                if (oldUrl.recommendation === 'RECOMMENDATION_BUMP') { //jshint ignore: line
                                    oldUrl.recommendation = newUrl.recommendation;
                                }
                                // FUTURE: more adjustments?
                            }
                        }
                    }
                    if (!found) {
                        // By which apps is this new entry whitelisted?
                        // 1. see if the IP is whitelisted by some app
                        // 2. see if the domain is whitelisted by some app
                        newUrl.whitelistedBy = [];
                        ipWhitelistedBy(newUrl, whitelistedDomainsIps);
                        if (newUrl.whitelistedBy.length === 0) {
                            domainWhitelistedBy(newUrl, whitelistedDomainsIps);
                        }
                        // Sort the apps that are whitelisting this connection
                        newUrl.whitelistedBy = newUrl.whitelistedBy.sort();
                        // If the list is empty, make it undefined to avoid empty brackets in the UI
                        if (newUrl.whitelistedBy.length === 0) {
                            newUrl.whitelistedBy = undefined;
                        }

                        newUrl.currentConfig = getCurrentConfiguration(newUrl);
                        newUrl.recommendedConfig = getRecommendedConfiguration(newUrl);
                        newUrl.testingConfig = 'NO_CHANGE';
                        if (newUrl.flag === 'domain') {
                            if (!angular.isDefined(newUrl.selectedDomain)) {
                                newUrl.selectedDomain = newUrl.recordedDomain;
                            }
                            newUrl.selectedIp = undefined;
                        } else {
                            // has no domain or the ip was found in an app
                            newUrl.selectedIp = newUrl.recordedIp;
                            newUrl.selectedDomain = undefined;
                        }
                        recordedUrls.push(newUrl);
                        resultsNew = resultsNew + 1;
                    }
                }
            }
            // show success message
            NotificationService.info('ADMINCONSOLE.SERVICE.MANUAL_RECORDING.NOTIFICATION.SUCCESS_LOADED_RESULTS',
                {new: resultsNew, total: recordedUrls.length});
            return recordedUrls;
        });
    }

    let statusChecker;
    let statusCheckActive = false;

    function checkStatus(callback) {
        if (statusCheckActive) {
            return;
        }
        statusCheckActive = true;
        $http.get(PATH_STATUS).
        then(function(response) {
            callback(response.data);
        }).
        finally(function() {
            statusCheckActive = false;
        });
    }

    function activateStatusCheck(callback) {
        if (angular.isDefined(statusChecker)) {
            return;
        }
        statusChecker = $interval(function() {
            checkStatus(callback);
        }, 2000);
    }

    function deactivateStatusCheck() {
        if (angular.isDefined(statusChecker)){
            $interval.cancel(statusChecker);
            statusChecker = undefined;
        }
    }

    function getCurrentConfiguration(recordedUrl) {
        if (!angular.isDefined(recordedUrl)) {
            return 'FILTER'; // this is the default, if we know nothing else
        }
        if (angular.isDefined(recordedUrl.whitelistedBy) && recordedUrl.whitelistedBy.length > 0) {
            return 'ALLOW'; // domain is whitelisted
        }
        return 'FILTER'; // this is the default, if we know nothing else
    }

    function getRecommendedConfiguration(recordedUrl) {
        if (!angular.isDefined(recordedUrl) || !angular.isDefined(recordedUrl.recommendation)) {
            return 'NONE'; // no recommendation
        }
        if (recordedUrl.recommendation === 'RECOMMENDATION_BUMP') {
            return 'FILTER'; //  no recommendation
        }
        if (recordedUrl.recommendation === 'RECOMMENDATION_WHITELIST') {
            return 'ALLOW';
        }
        return 'NONE';
    }

    return {
        start: start,
        stop: stop,
        getResult: getResult,
        activateStatusCheck: activateStatusCheck,
        deactivateStatusCheck: deactivateStatusCheck
    };
}
