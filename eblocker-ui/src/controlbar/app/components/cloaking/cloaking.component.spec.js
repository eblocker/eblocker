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
import 'angular-mocks';

/* jshint expr:true */
describe('App controlbar; Component: cloaking', function() {
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    var $componentController, $q, $rootScope, $httpBackend, ctrl, mockLoggerService, mockUserAgentList,
        mockCloakedUserAgent, $mdDialog;

    mockLoggerService = {
        info: function(param) {
            // nothing to do for now
        },
        error: function(param) {
            // nothing to do for now
        }
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('logger', mockLoggerService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _$q_, _$rootScope_, _$httpBackend_, _$mdDialog_) {
        $componentController = _$componentController_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;
        $mdDialog = _$mdDialog_;

        mockUserAgentList = ['Off','iPhone','PC (Linux)','iPad','PC (Windows)','Mac','Android'];
        mockCloakedUserAgent = {
            agentSpec: 'cloaked-user-agent',
            agentName: 'Android',
            isCustomUserAgent: false
        };

        $httpBackend.when('GET', '/api/useragent/list').respond(200, mockUserAgentList);
        $httpBackend.when('GET', '/api/useragent/cloaked').respond(200, mockCloakedUserAgent);
        $httpBackend.when('GET', '/api/controlbar/ssl/status').respond(200, {data: {globalSslStatus: true}});
        $httpBackend.when('GET', '/api/controlbar/device').respond(200, {data: {sslEnabled: true}});

        ctrl = $componentController('cloaking', {$mdDialog: $mdDialog}, {});
        ctrl.closeDropdown = {
            now: function() {
                // do nothing.
            }
        };

        ctrl.$onInit();
        $httpBackend.flush();
    }));

    describe('initially', function() {
        beforeEach(function() {
            // We have to make angular resolve the promise (WhitelistService), since it is not done
            // automatically because no template is involved:
            // https://stackoverflow.com/questions/24211312/angular-q-when-is-not-resolved-in-karma-unit-test
            $rootScope.$apply();
        });

        it('controller should be defined', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });

        it('user agent list should be initialized', function() {
            expect(angular.isDefined(ctrl.userAgentList)).toBe(true);
            expect(ctrl.userAgentList.length).toEqual(mockUserAgentList.length);
        });

        it('cloaked user agent should be initialized', function() {
            expect(angular.isDefined(ctrl.selectedUserAgent)).toBe(true);
            expect(ctrl.selectedUserAgent).toMatch('Android');
        });
    });

    describe('getUserAgentList', function() {
        it('user agent list should have set mobile flag', function() {
            ctrl.userAgentList.forEach((profile) => {
                if (profile.name === 'iPad' || profile.name === 'iPhone' || profile.name === 'Android') {
                    expect(profile.isMobile).toBe(true);
                } else {
                    expect(profile.isMobile).toBe(false);
                }
            });
        });

        it('user agent list should be sorted by name', function() {
            expect(ctrl.userAgentList[0].name).toMatch('Android');
            expect(ctrl.userAgentList[1].name).toMatch('iPad');
            expect(ctrl.userAgentList[2].name).toMatch('iPhone');
            expect(ctrl.userAgentList[3].name).toMatch('Mac');
        });
    });

    describe('setUserAgent', function() {
        it('should make a REST call to update user agent', function() {
            $httpBackend.expect('PUT', '/api/useragent/cloaked').respond(200);
            ctrl.setUserAgent('iPad');
            $httpBackend.flush();
        });
    });

    describe('setCustomUserAgent', function() {
        it('open the custom agent dialog', function() {
            /* jshint ignore:start */
            spyOn($mdDialog, 'show').and.callThrough();
            ctrl.setCustomUserAgent();
            expect($mdDialog.show).toHaveBeenCalled();
            /* jshint ignore:end */
        });
    });
});
