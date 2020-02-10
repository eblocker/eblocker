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
describe('Whitelist service', function() { // jshint ignore: line
    beforeEach(angular.mock.module('template.dashboard.app'));
    beforeEach(angular.mock.module('eblocker.dashboard'));

    var $httpBackend, $q, whitelistService;

    var mockWhitelist = {
        'eblocker.com': {
            ads: true,
            trackers: true
        },
        'eblocker.de': {
            ads: false,
            trackers: true
        },
        'eblocker.org': {
            ads: true,
            trackers: false
        }
    };

    const mockRegistrationInfo = {
        loadProductInfo: function() {
            const deferred = $q.defer();
            const response = {
                data: []
            };
            deferred.resolve(response);
            return deferred.promise;
        },
        getProductInfo: function() {},
        getRegistrationInfo: function() {
            return {productInfo: {}};
        }
    };

    const mockCardService = {
        getDashboardData: function() {}
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('registration', mockRegistrationInfo);
        $provide.value('CardService', mockCardService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $provide.factory('customLoader', function ($q) {
            return function () {
                var deferred = $q.defer();
                deferred.resolve({});
                return deferred.promise;
            };
        });
        $translateProvider.useLoader('customLoader');
    }));

    beforeEach(inject(function(_WhitelistService_, _$httpBackend_, _$q_) {
        $httpBackend = _$httpBackend_;
        $q = _$q_;
        $httpBackend.when('GET', '/api/token/DASHBOARD').respond(200, {});
        $httpBackend.when('GET', '/api/settings').respond(200, {});
        $httpBackend.when('GET', '/controlbar/console/ip').respond(200, {});
        $httpBackend.when('GET', '/api/device').respond(200, {});
        $httpBackend.when('GET', '/api/dashboard/users').respond(200, {});
        $httpBackend.when('GET', '/api/dashboardcard').respond(200, []);
        whitelistService = _WhitelistService_;
        // $httpBackend.flush();
    }));

    describe('test getMessages', function() {
        it('should make a REST call and return whitelist', function() {
            $httpBackend.expect('GET', '/summary/whitelist/all').respond(200, mockWhitelist);
            whitelistService.getWhitelist().then(function(response) {
                Object.keys(response.data).forEach(function(item) {
                    mockWhitelist.hasOwnProperty(item);
                });
            });
            $httpBackend.flush();
        });
    });

    describe('test setMessages', function() {
        it('should make a REST call', function() {
            $httpBackend.expect('PUT', '/summary/whitelist/all').respond(200);
            whitelistService.setWhitelist([]);
            $httpBackend.flush();
        });
    });

    describe('test updateWhitelistEntry', function() {
        it('should make a REST call', function() {
            $httpBackend.expect('PUT', '/summary/whitelist/update').respond(200);
            whitelistService.updateWhitelistEntry({});
            $httpBackend.flush();
        });
    });
});
