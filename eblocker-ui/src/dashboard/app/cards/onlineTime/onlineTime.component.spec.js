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
/* jshint expr:true */
import 'angular-mocks';
describe('Component: dashboardOnlineTime', function() {

    beforeEach(angular.mock.module('eblocker.dashboard'));
    // ** Not Sure why these modules have to be included here, since they are
    // already included in 'eblocker.dashboard'.
    // beforeEach(angular.mock.module('eblocker.logger'));
    // beforeEach(angular.mock.module('eblocker.security'));
    // beforeEach(angular.mock.module('device'));


    let $componentController,
        $q,
        LocalTimestampService,
        mockUserProfile,
        device,
        ctrl;

    mockUserProfile = {
        controlmodeTime: true,
        internetAccessContingents: {},
        controlmodeMaxUsage: true
    };

    let mockUserProfileService = {
        getUserProfile: function() {
            var deferred = $q.defer();
            var response = {
                data: mockUserProfile
            };
            deferred.resolve(response);
            return deferred.promise;
        }
    };

    let mockDeviceService = {
        getDevice: function() {
            var deferred = $q.defer();
            var response = {
                data: {}
            };
            deferred.resolve(response);
            return deferred.promise;
        }
    };

    const mockDataService = {
        registerComponentAsServiceListener: function() {},
        unregisterComponentAsServiceListener: function() {}
    };

    // beforeEach(inject(function(_$componentController_, _$q_, _device_) { // does not work: provider device ?!
    beforeEach(inject(function(_$componentController_, _$q_) {
        $q = _$q_;
        $componentController = _$componentController_;
        ctrl = $componentController('dashboardOnlineTime', {
                LocalTimestampService: {},
                onlineTime: {},
                UserProfileService: mockUserProfileService,
                CardService: {},
                DataService: mockDataService
            },
            {});
    }));

    describe('dashboardOnlineTime', function() {
        it('initially shows the user to have no time restrictions', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });
});
