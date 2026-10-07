/*
 * Copyright 2026 eBlocker Open Source GmbH
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
// jshint ignore: line
describe('App: settings; TimezoneService', function() {
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let timezoneService;

    beforeEach(inject(function(_TimezoneService_) {
        timezoneService = _TimezoneService_;
    }));

    describe('Testing splitTimezone', function() { // jshint ignore: line
        it('should return region and city', function() {
            const ret = timezoneService.splitTimezone('Europe/London');
            expect(ret.length).toEqual(2);
            expect(ret[0]).toEqual('Europe');
            expect(ret[1]).toEqual('London');
        });

        it('should pass through timezone without city', function() {
            const ret = timezoneService.splitTimezone('UTC');
            expect(ret.length).toEqual(1);
            expect(ret[0]).toEqual('UTC');
        });

        it('should process 3-level timezones', function() {
            const ret = timezoneService.splitTimezone('America/Argentina/Mendoza');
            expect(ret.length).toEqual(2);
            expect(ret[0]).toEqual('America');
            expect(ret[1]).toEqual('Argentina/Mendoza');
        });
    });
});
