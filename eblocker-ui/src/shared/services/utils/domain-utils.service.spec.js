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
describe('Domain utils service', function() { // jshint ignore: line

    let utils;

    beforeEach(angular.mock.module('eblocker.dashboard'));

    beforeEach(inject(function(_DomainUtilsService_) {
        utils = _DomainUtilsService_;
    }));

    it('checks whether a string is a domain', function() {
        const validDomains = ['example.com', '3com.com', 'test-.stanza.co', 'co.uk', 'FOO.BAR'];
        const invalidDomains = [undefined, '', '1.2.3.4', '1.2.3.4/32', 'localhost', 'localhost/42',
                                '1.2.3', '1.2.3.4.5', '42/23', '::1/a', '::1/129', '.org'];

        invalidDomains.forEach((domain) =>
            expect(utils.isDomain(domain)).withContext(domain).toEqual(false)
        );
        validDomains.forEach((domain) =>
            expect(utils.isDomain(domain)).withContext(domain).toEqual(true)
        );
    });
});
