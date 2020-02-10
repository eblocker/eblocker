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
describe('App controlbar; Component: OnlineTime', function() {
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    let $componentController, $q, $rootScope, $httpBackend, ctrl, mockLoggerService, mockLocalTimeStamp,
        mockUsage, mockDevice;

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

    beforeEach(inject(function(_$componentController_, _$q_, _$rootScope_, _$httpBackend_) {
        $componentController = _$componentController_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;

        mockLocalTimeStamp = {
            instant: {
                seconds: 1513608436, nanos: 63000000
            },
            hour: 15, minute: 47, day: 18, second: 16, dayOfWeek: 1, month: 12, year: 2017
        };

        mockUsage = {
            active: true,
            allowed: true,
            usedTime: {
                seconds: 11400,
                nanos: 871000000
            },
            accountedTime: {
                seconds: 11400,
                nanos: 871000000
            },
            maxUsageTime: {
                seconds: 86400,
                nanos: 0
            }
        };

        mockDevice = {
            effectiveUserProfile: {
                controlmodeMaxUsage: true,
                controlmodeTime: false,
                controlmodeUrls: false,
                internetAccessContingents: [
                    {
                        fromMinutes: 60,
                        onDay: 8,
                        tillMinutes: 1380
                    }
                ],
                maxUsageTimeByDay: {
                    FRIDAY: 1440,
                    MONDAY: 1440,
                    SATURDAY: 1440,
                    SUNDAY: 1440,
                    THURSDAY: 1440,
                    TUESDAY: 1440,
                    WEDNESDAY: 1440
                }
            }
        };

        $httpBackend.when('GET', '/api/controlbar/localtimestamp').respond(200, mockLocalTimeStamp);
        $httpBackend.when('GET', '/api/controlbar/device').respond(200, mockDevice);
        $httpBackend.when('GET', '/api/parentalcontrol/usage').respond(200, mockUsage);

        ctrl = $componentController('onlineTime', {}, {});

        $httpBackend.flush();
    }));

    describe('initially', function() {
        it('controller should be defined', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });

    describe('getTimeStamp', function() {
        it('should make a REST call to get timestamp and set usage and remaining time ', function() {
            // $httpBackend.expect('GET', '/api/localtimestamp').respond(200, mockLocalTimeStamp);
            // $httpBackend.expect('GET', '/api/parentalcontrol/usage').respond(200, mockUsage);
            ctrl.getTimeStamp().then(function success() {
                expect(angular.isDefined(ctrl.onlineTimeProfile.usage)).toBe(true);
                expect(angular.isDefined(ctrl.onlineTimeProfile.remainingTime)).toBe(true);
            });
            // $httpBackend.flush();
        });
    });

    describe('getRemainingTime', function() {
        it('should calculate remaining time', function() {
            const profile = mockDevice.effectiveUserProfile;
            const usage = { maxUsageTime: 3000, usedTime: 1000 };
            let weekDay = 2;
            let minute = 400;
            let ret = ctrl.getRemainingTime(weekDay, minute, profile, usage);
            expect(ret.hours).toEqual(0);
            expect(ret.minutes).toEqual(33);
        });
    });
});
