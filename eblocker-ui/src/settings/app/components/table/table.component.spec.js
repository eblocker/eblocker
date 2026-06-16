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

describe('App settings; Table component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl, $componentController, TableService;

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _TableService_) {
        $componentController = _$componentController_;
        TableService = _TableService_;
        ctrl = $componentController('ebTable', {}, {});
    }));

    function createTable(tableId) {
        const table = $componentController('ebTable', {}, {
            tableId: tableId,
            tableHeader: [
                {
                    label: 'Default',
                    sortingKey: 'name',
                    defaultSorting: true
                },
                {
                    label: 'Last online',
                    sortingKey: 'lastSeen'
                }
            ],
            tableData: [
                {
                    name: 'Alpha',
                    lastSeen: 2
                },
                {
                    name: 'Beta',
                    lastSeen: 1
                }
            ]
        });
        table.$onInit();
        return table;
    }

    describe('function isReducedWidth', function() {
        it('return false if edit mode is not set and details view is not defined', function() {
            expect(ctrl.isReducedWidth(false, false)).toBe(false);
        });

        it('return true if edit mode is set, but details view is not defined', function() {
            expect(ctrl.isReducedWidth(true, false)).toBe(true);
        });

        it('return true if edit mode is not set, but details view is defined', function() {
            expect(ctrl.isReducedWidth(false, true)).toBe(true);
        });

        it('return true if edit mode is set and details view is defined', function() {
            // this state shows the checkmark, but hides the details view icon, so it does not matter whether
            // the second param is true or false, once the first is true.
            expect(ctrl.isReducedWidth(true, true)).toBe(true);
        });
    });

    describe('table sorting', function() {
        it('restores changed sort column and order for the same table', function() {
            const tableId = TableService.getUniqueTableId('devices-table');
            const firstTable = createTable(tableId);

            firstTable.changeOrder('lastSeen');
            firstTable.changeOrder('lastSeen');

            const secondTable = createTable(tableId);

            expect(secondTable.orderKey).toBe('lastSeen');
            expect(secondTable.reverseOrder).toBe(true);
        });
    });
});
