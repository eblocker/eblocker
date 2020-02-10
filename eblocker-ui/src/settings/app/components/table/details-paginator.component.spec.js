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

describe('App settings; Details paginator component controller', function() { // jshint ignore: line
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl, $componentController, testList;

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_) {
        $componentController = _$componentController_;
        ctrl = $componentController('ebDetailsPaginator', {}, {});
        testList = [
            {id: 1, name: 'first entry'},
            {id: 2, name: 'second entry'},
            {id: 3, name: 'third entry'},
            {id: 4, name: 'fourth entry'}
        ];
    }));

    describe('function getNextEntry', function() {
        it('should return the second entry, if current entry is the first', function() {
            expect(ctrl.getNextEntry(testList, 0).id).toEqual(2);
        });
        it('should return the last entry, if current entry is second to last', function() {
            expect(ctrl.getNextEntry(testList, 2).id).toEqual(4);
        });
        it('should return no entry, if current entry is the last (no wrap around)', function() {
            expect(angular.isUndefined(ctrl.getNextEntry(testList, 3))).toBe(true);
        });
    });

    describe('function getPreviousEntry', function() {
        it('should return the second to last entry, if current entry is the last', function() {
            expect(ctrl.getPreviousEntry(testList, 3).id).toEqual(3);
        });
        it('should return the first entry, if current entry is the second', function() {
            expect(ctrl.getPreviousEntry(testList, 1).id).toEqual(1);
        });
        it('should return no entry, if current entry is the first (no wrap around)', function() {
            expect(angular.isUndefined(ctrl.getPreviousEntry(testList, 0))).toBe(true);
        });
    });
});
