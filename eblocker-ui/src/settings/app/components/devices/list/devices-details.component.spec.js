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

describe('App settings; Devices-Details component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl, $componentController;

    beforeEach(angular.mock.module(function($translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_) {
        $componentController = _$componentController_;
        ctrl = $componentController('devicesDetailsComponent', {
            $stateParams: {},
            STATES: {
                DEVICES: 'devices'
            },
            logger: {
                error: angular.noop,
                warn: angular.noop
            }
        }, {
            users: [],
            vpnHomeCertificates: []
        });
    }));

    describe('initially', function() {
        it('should create a controller instance', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });

    describe('showFixedIpMultipleAddressesWarning', function() {
        function setDevice(ipAddresses, ipAddressFixed, dhcpActive) {
            ctrl.dhcpActive = dhcpActive;
            ctrl.device = {
                ipAddresses: ipAddresses,
                ipAddressFixed: ipAddressFixed
            };
        }

        it('should warn when eBlocker runs DHCP and a fixed-IP device has multiple IPv4 addresses', function() {
            setDevice(['192.168.1.20', '192.168.1.21'], true, true);

            expect(ctrl.showFixedIpMultipleAddressesWarning()).toBe(true);
        });

        it('should not warn when DHCP is inactive', function() {
            setDevice(['192.168.1.20', '192.168.1.21'], true, false);

            expect(ctrl.showFixedIpMultipleAddressesWarning()).toBe(false);
        });

        it('should not warn when fixed IP assignment is disabled', function() {
            setDevice(['192.168.1.20', '192.168.1.21'], false, true);

            expect(ctrl.showFixedIpMultipleAddressesWarning()).toBe(false);
        });

        it('should not warn for a single IPv4 address', function() {
            setDevice(['192.168.1.20'], true, true);

            expect(ctrl.showFixedIpMultipleAddressesWarning()).toBe(false);
        });

        it('should only count IPv4 addresses', function() {
            setDevice(['192.168.1.20', 'fe80::1', 'fe80::2'], true, true);

            expect(ctrl.showFixedIpMultipleAddressesWarning()).toBe(false);
        });
    });
});
