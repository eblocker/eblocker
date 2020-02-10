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
// jshint ignore: line
describe('App: settings; DnsService', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let dnsService;

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_DnsService_) {
        dnsService = _DnsService_;
    }));

    describe('Testing updateDnsServer', function() { // jshint ignore: line
        it('should return a list the entry updated', function() {
            const list = [{id: 1, orderNumber: 1, server: 'test'}];
            const updated = {id: 1, orderNumber: 2, server: 'updated'};
            const ret = dnsService.updateDnsServer(list, updated);
            expect(ret[0].id).toEqual(1);
            expect(ret[0].orderNumber).toEqual(updated.orderNumber);
            expect(ret[0].server).toMatch(updated.server);
        });
    });

    describe('Testing updateOrderNumbers with three entries in list by', function() { // jshint ignore: line
        describe('moving first entry down in list by one', function() { // jshint ignore: line
            const list = [{id: 1, orderNumber: 0}, {id: 2, orderNumber: 1}, {id: 3, orderNumber: 2}];
            let newList;
            beforeEach(function() {
                const oldOrderNumber = 0;
                const newEntryId = 1;
                const newEntryOrderNumber = 1;
                list[0].orderNumber = newEntryOrderNumber; // mock update or entry
                newList = dnsService.updateOrderNumbers(list, newEntryId, newEntryOrderNumber, oldOrderNumber);
            });
            it('should return a list with same length as input list', function() {
                expect(newList.length).toEqual(list.length);
            });
            it('should reorder the array', function() {
                newList.forEach((entry) => {
                    if (entry.id === 1) {
                        // first entry in middle
                        expect(entry.orderNumber).toEqual(1);
                    } else if (entry.id === 2) {
                        // old middle entry at start
                        expect(entry.orderNumber).toEqual(0);
                    } else if (entry.id === 3) {
                        // last entry unchanged.
                        expect(entry.orderNumber).toEqual(2);
                    }
                });
            });
        });

        describe('moving last entry up in list by one', function() { // jshint ignore: line
            const list = [{id: 1, orderNumber: 0}, {id: 2, orderNumber: 1}, {id: 3, orderNumber: 2}];
            let newList;
            beforeEach(function() {
                const oldOrderNumber = 2;
                const newEntryId = 3;
                const newEntryOrderNumber = 1;
                list[2].orderNumber = newEntryOrderNumber; // mock update or entry
                newList = dnsService.updateOrderNumbers(list, newEntryId, newEntryOrderNumber, oldOrderNumber);
            });
            it('should return a list with same length as input list', function() {
                expect(newList.length).toEqual(list.length);
            });
            it('should reorder the array', function() {
                newList.forEach((entry) => {
                    if (entry.id === 1) {
                        // first entry unchanged
                        expect(entry.orderNumber).toEqual(0);
                    } else if (entry.id === 2) {
                        // old last entry in middle
                        expect(entry.orderNumber).toEqual(2);
                    } else if (entry.id === 3) {
                        // old middle entry at end
                        expect(entry.orderNumber).toEqual(1);
                    }
                });
            });
        });
    });

    describe('Test updateOrderNumbers with large list by', function() { // jshint ignore: line
        describe('moving last entry up', function() { // jshint ignore: line
            const list = [{id: 1, orderNumber: 0}, {id: 2, orderNumber: 1}, {id: 3, orderNumber: 2},
                {id: 4, orderNumber: 3}, {id: 5, orderNumber: 4}, {id: 6, orderNumber: 5}];
            let newList;
            beforeEach(function() {
                const oldOrderNumber = 5;
                const newEntryId = 6;
                const newEntryOrderNumber = 2;
                list[5].orderNumber = newEntryOrderNumber; // mock update or entry
                newList = dnsService.updateOrderNumbers(list, newEntryId, newEntryOrderNumber, oldOrderNumber);
            });
            it('should return a list with same length as input list', function() {
                expect(newList.length).toEqual(list.length);
            });
            it('should reorder the array', function() {
                newList.forEach((entry) => {
                    if (entry.id === 1) {
                        expect(entry.orderNumber).toEqual(0);
                    } else if (entry.id === 2) {
                        expect(entry.orderNumber).toEqual(1);
                    } else if (entry.id === 3) {
                        expect(entry.orderNumber).toEqual(3);
                    } else if (entry.id === 4) {
                        expect(entry.orderNumber).toEqual(4);
                    } else if (entry.id === 5) {
                        expect(entry.orderNumber).toEqual(5);
                    } else if (entry.id === 6) {
                        expect(entry.orderNumber).toEqual(2);
                    }
                });
            });
        });

        describe('moving second entry form orderNumber 1 to orderNumber 4', function() { // jshint ignore: line
            const list = [{id: 1, orderNumber: 0}, {id: 2, orderNumber: 1}, {id: 3, orderNumber: 2},
                {id: 4, orderNumber: 3}, {id: 5, orderNumber: 4}, {id: 6, orderNumber: 5}];
            let newList;
            beforeEach(function() {
                const oldOrderNumber = 1;
                const newEntryId = 2;
                const newEntryOrderNumber = 4;
                list[1].orderNumber = newEntryOrderNumber; // mock update or entry
                newList = dnsService.updateOrderNumbers(list, newEntryId, newEntryOrderNumber, oldOrderNumber);
            });
            it('should return a list with same length as input list', function() {
                expect(newList.length).toEqual(list.length);
            });

            it('should reorder the array', function() {
                newList.forEach((entry) => {
                    if (entry.id === 1) {
                        expect(entry.orderNumber).toEqual(0);
                    } else if (entry.id === 2) {
                        expect(entry.orderNumber).toEqual(4);
                    } else if (entry.id === 3) {
                        expect(entry.orderNumber).toEqual(1);
                    } else if (entry.id === 4) {
                        expect(entry.orderNumber).toEqual(2);
                    } else if (entry.id === 5) {
                        expect(entry.orderNumber).toEqual(3);
                    } else if (entry.id === 6) {
                        expect(entry.orderNumber).toEqual(5);
                    }
                });
            });
        });
    });

});
