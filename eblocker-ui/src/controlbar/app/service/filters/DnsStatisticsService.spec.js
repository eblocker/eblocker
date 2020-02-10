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
describe('DnsStatistics service', function() { // jshint ignore: line
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    let dnsStat;

    const bins = [
        {blockedQueriesByReason: {'TRACKERS': 13, 'ADS': 7}},
        {blockedQueriesByReason: {'TRACKERS': 0, 'ADS': 0}},
        {blockedQueriesByReason: {'TRACKERS': 0, 'ADS': 0}},
        {blockedQueriesByReason: {'TRACKERS': 2, 'ADS': 3}},
        {blockedQueriesByReason: {'TRACKERS': 0, 'ADS': 0}},
        {blockedQueriesByReason: {'TRACKERS': 0, 'ADS': 0}},
        {blockedQueriesByReason: {'TRACKERS': 1, 'ADS': 1}},
    ];

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $provide.value('$translate', {
            preferredLanguage: function() {
                return 'en';
            },
            storage: function() {
                return undefined;
            },
            storageKey: function() {
                return 'NG_TRANSLATE_LANG_KEY';
            },
            use: function() {
                return 'en';
            }
        });
        $provide.factory('customLoader', function ($q) {
            return function () {
                let deferred = $q.defer();
                deferred.resolve({});
                return deferred.promise;
            };
        });
        $translateProvider.useLoader('customLoader');
    }));

    beforeEach(inject(function(_$httpBackend_, _DnsStatistics_) {
        dnsStat = _DnsStatistics_;
    }));

    describe('getBlockedInLastMinutes', function() {
        it('should be defined', function() {
            expect(angular.isFunction(dnsStat.getBlockedInLastMinutes)).toBe(true);
        });

        it('should return correct number of blocked requests for given minutes', function() {
            // getBlockedInLastMinutes(bins, minutes, filterLists, listType)
            expect(dnsStat.getBlockedInLastMinutes(bins, 7, 'ADS')).toEqual(11);
            expect(dnsStat.getBlockedInLastMinutes(bins, 1, 'ADS')).toEqual(1);
            expect(dnsStat.getBlockedInLastMinutes(bins, 3, 'ADS')).toEqual(1);
            expect(dnsStat.getBlockedInLastMinutes(bins, 4, 'ADS')).toEqual(4);
        });
    });


});
