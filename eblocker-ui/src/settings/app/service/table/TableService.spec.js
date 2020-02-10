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
describe('App: settings; TableService', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let service;

    const mockWindowService = {
        innerWidth: 800
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('$window', mockWindowService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_TableService_) {
        service = _TableService_;
    }));

    describe('Testing getNumOfVisibleItems for large tables', function() { // jshint ignore: line
        let testTableId;
        beforeEach(function init() {
            mockWindowService.innerWidth = 800;
            testTableId = service.getUniqueTableId('large-test-table');
        });

        it('should return default value (10) if table size is more than largest option - 20', function() {
            const expectation = 10;
            const tableSize = 33;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });

        it('should return default value (10) if table size is greater than 10, but smaller than 15', function() {
            const expectation = 10;
            const tableSize = 11;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });

        it('should return \'ALL\' if table size is in between 5 and 10', function() {
            const expectation = 'ALL';
            const tableSize = 7;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });

        it('should return \'ALL\' if table size is equal to 10', function() {
            const expectation = 'ALL';
            const tableSize = 10;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });

        it('should return \'ALL\' if table size is smaller than 5', function() {
            const expectation = 'ALL';
            const tableSize = 4;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });
    });

    describe('Testing getNumOfVisibleItems for small tables', function() { // jshint ignore: line
        let testTableId;
        beforeEach(function init() {
            mockWindowService.innerWidth = 500;
            testTableId = service.getUniqueTableId('small-test-table');
        });

        it('should return 5 if table size is more than largest option - 15', function() {
            const expectation = 5;
            const tableSize = 33;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });

        it('should return \'ALL\' if table size is greater than 1, but smaller than 5', function() {
            const expectation = 'ALL';
            const tableSize = 3;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });

        it('should return \'ALL\' if table size is 1', function() {
            const expectation = 'ALL';
            const tableSize = 1;
            const options = service.getTablePaginatorOptions(tableSize);
            const numVisibleItem = service.getNumOfVisibleItems(testTableId, options);
            expect(numVisibleItem).toEqual(expectation);
        });
    });
});
