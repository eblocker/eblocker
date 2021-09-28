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

function Controller(logger, DoctorService, TableService, STATES) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.userExperienceLevels = ['NOVICE', 'EXPERT'];
    vm.selectedExperienceLevel = 'NOVICE';

    vm.getSelectedExperienceLevel = getSelectedExperienceLevel;
    vm.setExperienceLevel = setExperienceLevel;

    vm.$onInit = function() {
        runDiagnosis();
        vm.templateCallback = {
            showMoreInfo: showMoreInfo,
            hasDetails: hasDetails,
            isError : isError,
            isWarning : isWarning,
            isInfo : isInfo,
            isOkay : isOkay
        };
    }

    function setExperienceLevel(level) {
        vm.selectedExperienceLevel = level;
        filterByLevel();
    }

    function getSelectedExperienceLevel() {
        return vm.selectedExperienceLevel;
    }

    function showMoreInfo(dr) {
        return isError(dr) || isWarning(dr) || isInfo(dr);
    }

    function hasDetails(dr) {
        return dr.dynamicInfo && dr.dynamicInfo !==  "";
    }

    function runDiagnosis() {
        DoctorService.runDiagnosis().then(function success(response) {
            vm.diagnosisResult = response.data;
           filterByLevel();
        }, function error(response) {
            logger.error('Error running diagnosis', response);
        });
    }

    function filterByLevel() {
        vm.filteredDiagnosisResult = vm.diagnosisResult.filter(function(dr) {
            return dr.audience === 'EVERYONE' || dr.audience === vm.selectedExperienceLevel;
        });
    }

    function isError(dr) {
        return dr.severity === 'FAILED_PROBE';
    }

    function isWarning(dr) {
        return dr.severity === 'RECOMMENDATION_NOT_FOLLOWED' ||
            dr.severity === 'ANORMALY'
    }

    function isInfo(dr) {
        return dr.severity === 'HINT';
    }

    function isOkay(dr) {
        return dr.severity === 'GOOD';
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
            flexGtXs: 10,
            sortingKey: 'severityOrder'
        },
        {
            label: 'ADMINCONSOLE.DOCTOR.DIAGNOSIS.TABLE.COLUMN.MESSAGE',
            flexGtXs: 55,
            isSortable: false
        }
    ];

    vm.detailsState = STATES.DOCTOR_DIAGNOSIS_DETAILS;
    // ## END: TABLE
}
