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
export default {
    templateUrl: 'app/components/doctor/diagnosis/doctor-diagnosis.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, DoctorService, TableService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.$onInit = function() {
        runDiagnosis();
    };

    function runDiagnosis() {
        DoctorService.runDiagnosis().then(function success(response) {
            vm.diagnosisResult = response.data;
        }, function error(response) {
            logger.error('Error running diagnosis', response);
        });
    }

    // ** START: TABLE
    vm.tableId = TableService.getUniqueTableId('doctor-diagnosis-table');
    vm.tableHeaderConfig = [
        {
            label: '',
            isSortable: false,
            showOnSmallTable: false,
            isXsColumn: true
        },
        {
            label: 'ADMINCONSOLE.DOCTOR.DIAGNOSIS.TABLE.COLUMN.SEVERITY',
            isSortable: true,
            sortingKey: 'severity'
        },
        {
            label: 'ADMINCONSOLE.DOCTOR.DIAGNOSIS.TABLE.COLUMN.AUDIENCE',
            flexGtXs: 15,
            isSortable: false
        },
        {
            label: 'ADMINCONSOLE.DOCTOR.DIAGNOSIS.TABLE.COLUMN.MESSAGE',
            flexGtXs: 55,
            isSortable: false
        }
    ];
    // ## END: TABLE
}
