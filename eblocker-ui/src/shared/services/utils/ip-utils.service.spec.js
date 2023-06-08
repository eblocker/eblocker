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
            expect(sorted[0]).toEqual(el4);
            expect(sorted[1]).toEqual(el2);
            expect(sorted[2]).toEqual(el6);
            expect(sorted[3]).toEqual(el7);
            expect(sorted[4]).toEqual(el5);
            expect(sorted[5]).toEqual(el3);
            expect(sorted[6]).toEqual(el1);
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
});
