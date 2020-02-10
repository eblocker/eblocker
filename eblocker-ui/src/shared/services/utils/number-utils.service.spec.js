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
describe('Number utils service', function() { // jshint ignore: line

    let utils;

    beforeEach(angular.mock.module('eblocker.dashboard'));

    beforeEach(inject(function(_NumberUtilsService_) {
        utils = _NumberUtilsService_;
    }));

    describe('test convertToStringWithSeparator', function() {
        it('should convert 10000009 into "10 000 009" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(10000009, ' ');
            expect(string).toMatch('10 000 009');
        });
        it('should convert 1000009 into "1 000 009" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(1000009, ' ');
            expect(string).toMatch('1 000 009');
        });
        it('should convert 100009 into "100 009" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(100009, ' ');
            expect(string).toMatch('100 009');
        });
        it('should convert 10009 into "10 009" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(10009, ' ');
            expect(string).toMatch('10 009');
        });
        it('should convert 1009 into "1 009" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(1009, ' ');
            expect(string).toMatch('1 009');
        });
        it('should convert 109 into "109" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(109, ' ');
            expect(string).toMatch('109');
        });
        it('should convert 19 into "19" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(19, ' ');
            expect(string).toMatch('19');
        });
        it('should convert 9 into "9" when second arg is a space', function() {
            const string = utils.convertToStringWithSeparator(9, ' ');
            expect(string).toMatch('9');
        });

        it('should convert 10000 into "10&#8201;000" when second arg is a small space', function() {
            const string = utils.convertToStringWithSeparator(10000, '&#8201;');
            expect(string).toMatch('10&#8201;000');
        });
    });

});
