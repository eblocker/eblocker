/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
describe('IP utils service', function() { // jshint ignore: line

    let utils;

    beforeEach(angular.mock.module('eblocker.dashboard'));

    beforeEach(inject(function(_IpUtilsService_) {
        utils = _IpUtilsService_;
    }));

    describe('test sortByVersion', function() {
        it('orders a list of IP addresses by IP protocol version', function() {
            const el1 = 'fe80::1:2:3:4';
            const el2 = '192.168.0.100';
            const el3 = '2a04:5432:abcd:ef:1:2:3:4';
            const el4 = '192.168.0.23';
            const el5 = '2a04:5432::1:2:3:4';
            const el6 = '::1';
            const el7 = '2::';
            const unsorted = [el1, el2, el3, el4, el5, el6, el7];
            const sorted = utils.sortByVersion(unsorted);
            expect(sorted).toEqual([el4, el2, el6, el7, el5, el3, el1]);
        });
    });

    describe('test sortingKey', function() {
        it('returns sorting key for an IP address', function() {
            expect(utils.sortingKey('123.45.6.7')).toEqual('ipv4_123.045.006.007');
            expect(utils.sortingKey('::')).toEqual('ipv6_0000:0000:0000:0000:0000:0000:0000:0000');
            expect(utils.sortingKey('::1')).toEqual('ipv6_0000:0000:0000:0000:0000:0000:0000:0001');
            expect(utils.sortingKey('2::')).toEqual('ipv6_0002:0000:0000:0000:0000:0000:0000:0000');
            expect(utils.sortingKey('2a04:5432::1:2:3:4')).toEqual('ipv6_2a04:5432:0000:0000:0001:0002:0003:0004');
        });
    });

    describe('test isIpv4Address', function() {
        it('checks whether a string is an IPv4 address', function() {
            const invalidAddresses = [undefined, '', '42', 'localhost', 'one.one.one.one',
                                      '1.2.3.4.5', '1.2.3.256', '...', '::1'];
            const validAddresses = ['127.0.0.1', '1.1.1.1', '255.255.255.255', '0.0.0.0', '192.168.1.2'];

            invalidAddresses.forEach((address) =>
                expect(utils.isIpv4Address(address)).withContext(address).toEqual(false)
            );
            validAddresses.forEach((address) =>
                expect(utils.isIpv4Address(address)).withContext(address).toEqual(true)
            );
        });
    });

    describe('test isIpv6Address', function() {
        it('checks whether a string is an IPv6 address', function() {
            const invalidAddresses = [undefined, '', '42', 'localhost', 'one.one.one.one',
                                      '1.2.3.4', 'abcd::ef::1',
                                      '1:2:3:4::5:6:7:8', '1:2:3:4:5:6:7'];
            const validAddresses = ['::', '::1', '1:2:3:4:5:6:7:8',
                                    '2a04:5432:abcd:ef:1:2:3:4', '2A04:5432:ABCD:EF:1:2:3:4'];

            invalidAddresses.forEach((address) =>
                expect(utils.isIpv6Address(address)).withContext(address).toEqual(false)
            );
            validAddresses.forEach((address) =>
                expect(utils.isIpv6Address(address)).withContext(address).toEqual(true)
            );
        });
    });

    describe('test isIpv4Range', function() {
        it('checks whether a string is an IPv4 address range', function() {
            const validRanges = ['17.0.0.0/8', '1.2.3.4/32'];
            const invalidRanges = [undefined, '', 'localhost/23', '1.2.3.4', '1.2.3.4/a', '1.2.3.4/33', '::1/42'];

            invalidRanges.forEach((ipRange) =>
                expect(utils.isIpv4Range(ipRange)).withContext(ipRange).toEqual(false)
            );
            validRanges.forEach((ipRange) =>
                expect(utils.isIpv4Range(ipRange)).withContext(ipRange).toEqual(true)
            );
        });
    });

    describe('test isIpv6Range', function() {
        it('checks whether a string is an IPv6 address range', function() {
            const validRanges = ['2603:1000::/25', 'ffff:FFFF:ffff:FFFF:ffff:FFFF:ffff:FFFF/128'];
            const invalidRanges = [undefined, '', 'localhost/42', '::1', '::1/a', '::1/129', '1.2.3.4/32'];

            invalidRanges.forEach((ipRange) =>
                expect(utils.isIpv6Range(ipRange)).withContext(ipRange).toEqual(false)
            );
            validRanges.forEach((ipRange) =>
                expect(utils.isIpv6Range(ipRange)).withContext(ipRange).toEqual(true)
            );
        });
    });

    describe('test isIpAddressOrRange', function() {
        it('checks whether a string is an IP address or a range of IP addresses', function() {
            const validAddresses = ['1.2.3.4', '1.2.3.4/32',
                                    '::1', '2603:1000::/25', 'ffff:FFFF:ffff:FFFF:ffff:FFFF:ffff:FFFF/128'];
            const invalidAddresses = [undefined, '', 'localhost', 'localhost/42', '1.2.3', '1.2.3.4.5', '42/23',
                                      '::1/a', '::1/129'];

            invalidAddresses.forEach((address) =>
                expect(utils.isIpAddressOrRange(address)).withContext(address).toEqual(false)
            );
            validAddresses.forEach((address) =>
                expect(utils.isIpAddressOrRange(address)).withContext(address).toEqual(true)
            );
        });
    });
});
