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
describe('Component: dashboardWhitelistDns', function() {
    beforeEach(angular.mock.module('eblocker.dashboard'));

    let $componentController, ctrl, mockDeviceService, mockSslService;

    mockDeviceService = {
        getDevice: function() {
            return {};
        },
        update: function() {

        }
    };

    mockSslService = {
        getSslStatus: function() {
            return true;
        }
    };

    beforeEach(angular.mock.module(function($provide) {
        $provide.value('DeviceService', mockDeviceService);
        $provide.value('SslService', mockSslService);
    }));

    beforeEach(inject(function(_$componentController_) {
        $componentController = _$componentController_;
        ctrl = $componentController('dashboardWhitelistDns', {}, {});
    }));

    /*describe('initially', function() {
        it('should create a controller instance', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });*/

});
