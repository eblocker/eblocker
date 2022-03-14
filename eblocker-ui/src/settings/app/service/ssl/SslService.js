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
export default function SslService($http, $q, NotificationService, DataCachingService, LanguageService) {
    'ngInject';

    const PATH_SSL_STATUS = '/api/adminconsole/ssl/status';
    const PATH_ATA_STATUS = '/api/adminconsole/ata/status';
    const PATH_SSL_STATUS_RENEWAL = '/api/adminconsole/ssl/status/renewal';
    const PATH_SSL_ROOTCA = '/api/adminconsole/ssl/rootca';
    const PATH_SSL_ROOTCA_OPTIONS = PATH_SSL_ROOTCA + '/options';
    const PATH_SSL_CERT_STATUS = '/api/adminconsole/ssl/certs/status';
    const PATH_SSL_WHITELIST = '/api/adminconsole/ssl/whitelist';
    const PATH_SSL_ERRORS = '/api/adminconsole/ssl/errors';
    const PATH_SSL_RECORDING = '/api/adminconsole/ssl/errors/recording';
    const PATH_APPS = '/api/adminconsole/trusteddomains/onlyenabled';

    let sslSettings = {
        // current / desired state
        enabled: true, // initially true, so that the tabs are available for deep links

        // dialog control
        step: 0,           // tab index
        generateCa: false, // show ca generation
        renew: false,      // regenerating ca

        // state
        certificatesReady: false,       // certificates available
        waitingForCertificates: false,  // waiting for creation of certificates
        rootCertificate: {},            // root certificate for summary
        renewalCertificateReady: false, // renewal certificate available

        recordingEnabled: true, // initially true, so that the tab is available for deep links

        display: {}
    };

    let sslStatusCache;

    function getStatus(reload) {
        sslStatusCache = DataCachingService.loadCache(sslStatusCache, PATH_SSL_STATUS, reload)
            .then(function (response) {
                sslSettings.enabled = response.data;
                return response;
            }, function (response) {
                NotificationService.error('ADMINCONSOLE.SERVICE.SSL.ERROR_SERVICE_SSL_GET_STATUS', response);
                return $q.reject(response);
            });
        return sslStatusCache;
    }

    function invalidateCache() {
        sslStatusCache = undefined;
    }

    function getUpdatedSettingsRenewalStatus() {
        return $http.get(PATH_SSL_STATUS_RENEWAL).then(function success(response) {
            sslSettings.renewalCertificateReady = response.data.renewalCertificateAvailable;
            const dateFormat = 'ADMINCONSOLE.SSL_STATUS.DATE_FORMAT';
            const currentCertificate = response.data.currentCertificate;
            if (angular.isDefined(currentCertificate) && angular.isDefined(currentCertificate.distinguishedName)) {
                sslSettings.display.cn = currentCertificate.distinguishedName.commonName;
                sslSettings.display.validityNotBefore = LanguageService.getDate(currentCertificate.notBefore,
                    dateFormat);
                sslSettings.display.validityNotAfter = LanguageService.getDate(currentCertificate.notAfter,
                    dateFormat);
            }
            const renewalCertificate = response.data.renewalCertificate;
            if (angular.isDefined(renewalCertificate) && angular.isDefined(renewalCertificate.distinguishedName)) {
                sslSettings.display.renewalCn = renewalCertificate.distinguishedName.commonName;
                sslSettings.display.renewalValidityNotBefore = LanguageService.getDate(renewalCertificate.notBefore,
                    dateFormat);
                sslSettings.display.renewalValidityNotAfter = LanguageService.getDate(renewalCertificate.notAfter,
                    dateFormat);
            }
            sslSettings.caRenewWeeks = response.data.caRenewWeeks;
            return sslSettings;
        }, standardError);
    }

    function setStatus(status) {
        return $http.post(PATH_SSL_STATUS, status).then(function (response) {
            sslSettings.enabled = status.enabled;
            return response;
        }, function (response) {
            return $q.reject(response);
        }).finally(function () {
            invalidateCache();
        });
    }

    function setAtaStatus(status) {
        return $http.post(PATH_ATA_STATUS, status).then(function (response) {
            return response.data;
        }, function (response) {
            return $q.reject(response);
        });
    }

    function getAtaStatus() {
        return $http.get(PATH_ATA_STATUS).then(function (response) {
            return response.data;
        }, function (response) {
            return $q.reject(response);
        });
    }

    function setRootCa(caOptions) {
        return $http.post(PATH_SSL_ROOTCA, caOptions).then(function (response) {
            return response;
        }, function (response) {
            return $q.reject(response);
        });
    }

    function getRootCa() {
        return $http.get(PATH_SSL_ROOTCA).then(function (response) {
            sslSettings.rootCertificate = response.data;
            const dateFormat = 'ADMINCONSOLE.SSL_STATUS.DATE_FORMAT';
            sslSettings.rootCertificate.notAfter = LanguageService.getDate(sslSettings.rootCertificate.notAfter,
                dateFormat);
            return response;
        }, function (response) {
            return $q.reject(response);
        });
    }

    function getRootCaOptions() {
        return $http.get(PATH_SSL_ROOTCA_OPTIONS).then(standardSuccess, standardError);
    }

    function getSslCertStatus() {
        return $http.get(PATH_SSL_CERT_STATUS).then(function (response) {
            sslSettings.certificatesReady = response.data;
            return response;
        }, function (response) {
            return $q.reject(response);
        });
    }

    // Queries all enabled appmodules
    // returns a dictionary with 'domain'-'>appname' and 'ip'->'appname'
    // where 'domain'/'ip' is whitelisted by the app identified by 'appname'
    function getAllWhitelistedDomains() {
        return $http.get(PATH_APPS).catch(
            function (response) {
                NotificationService.error('ADMINCONSOLE.SERVICE.SSL.ERROR_SERVICE_SSL_GETWHITELISTEDDOMAINS_APPS',
                    response);
            }).then(function (response) { // jshint ignore: line

                if (!angular.isObject(response) && !angular.isArray(response.data)) {
                    return {};
                }

                const whitelistedDomainsIps = {};

                for (let i = 0; i < response.data.length; i++) {
                    let appModule = response.data[i];
                    let name = angular.isString(appModule.name) && appModule.name !== '' ? appModule.name : '-';
                    const domainIps = angular.isArray(appModule.whitelistedDomainsIps) ?
                        appModule.whitelistedDomainsIps : [];

                    domainIps.forEach((domainIp) => {
                        if (angular.isDefined(domainIp)) {
                            if (!(domainIp in whitelistedDomainsIps)) {
                                whitelistedDomainsIps[domainIp] = [];
                            }
                            whitelistedDomainsIps[domainIp].push(name);
                        }
                    });
                }
                return whitelistedDomainsIps;
            }
        );
    }

    //
    // Used for Dialog
    //
    function parseAndSave(module) {
        /*let newWhitelistUrl = {};
        newWhitelistUrl.name = name;
        newWhitelistUrl.url = url;
        $http.post('/ssl/whitelist', newWhitelistUrl).success(function(){
            // TODO: reset name and url textfield?
            $scope.whitelistUrls = [];
            getOnlyEnabledAppWhitelistModules();
        });
        */

        module.domains = module.domainsText.split('\n'); // required for trusted websites
        return save(module);
    }

    function save(module) {
        return $http.post(PATH_SSL_WHITELIST, module).then(function (response) {
            return response.data;
        }, function (response) {
            return $q.reject(response);
        }).finally(function () {
            invalidateCache();
        });
    }

    function getErrors() {
        return $http.get(PATH_SSL_ERRORS).then(standardSuccess, standardError);
    }

    function clearErrors() {
        return $http.delete(PATH_SSL_ERRORS).then(standardSuccess, standardError);
    }

    function getSslErrorRecordingEnabled() {
        return $http.get(PATH_SSL_RECORDING).then(function success(response) {
            sslSettings.recordingEnabled = response.data.enabled;
            return response;
        }, standardError);
    }

    function setSslErrorRecordingEnabled(enabled) {
        return $http.put(PATH_SSL_RECORDING, {enabled: enabled}).then(function success(response) {
            sslSettings.recordingEnabled = response.data.enabled;
            return response;
        }, standardError);
    }

    function getSslSettings() {
        return angular.copy(sslSettings);
    }

    function setSslSettings(settings) {
        sslSettings = settings;
    }

    return {
        getStatus: getStatus,
        getUpdatedSettingsRenewalStatus: getUpdatedSettingsRenewalStatus,
        setStatus: setStatus,
        setAtaStatus: setAtaStatus,
        getAtaStatus: getAtaStatus,
        getRootCa: getRootCa,
        setRootCa: setRootCa,
        getRootCaOptions: getRootCaOptions,
        getSslCertStatus: getSslCertStatus,
        getAllWhitelistedDomains: getAllWhitelistedDomains,
        parseAndSave: parseAndSave,
        save: save,
        getErrors: getErrors,
        clearErrors: clearErrors,
        getSslErrorRecordingEnabled: getSslErrorRecordingEnabled,
        setSslErrorRecordingEnabled: setSslErrorRecordingEnabled,
        setSslSettings: setSslSettings,
        getSslSettings: getSslSettings,
        invalidateCache: invalidateCache
    };

    function standardSuccess(response) {
        return response;
    }

    function standardError(response) {
        return $q.reject(response);
    }
}
