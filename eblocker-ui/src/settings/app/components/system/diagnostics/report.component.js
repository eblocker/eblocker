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
    templateUrl: 'app/components/system/diagnostics/report.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, $window, $interval, DiagnosticsService, security) {
    'ngInject';
    'use strict';

    const vm = this;
    vm.createDiagnosticsReport = createDiagnosticsReport;

    vm.creatingDiagReport = false;

    function createDiagnosticsReport(){
        logger.debug('Starting to create the automated diagnostics report...');
        vm.creatingDiagReport = true;

        DiagnosticsService.startReportGeneration().then(function success(response) {
            logger.debug('Diagnostics report will be created... starting polling it. ', response);
            var statusPoll = $interval(function() {
                DiagnosticsService.getReportStatus().then(function success(response) {
                    switch(response.data) {
                    case 'PENDING':
                        return;
                    case 'ERROR':
                        logger.error('Error while generating report');
                        break;
                    case 'FINISHED':
                        $window.location = DiagnosticsService.downloadPath +
                            '?Authorization=Bearer+' + security.getToken();
                    }
                    $interval.cancel(statusPoll);
                    vm.creatingDiagReport = false;
                }, function error(response) {
                    logger.error('error polling report status');
                    $interval.cancel(statusPoll);
                    vm.creatingDiagReport = false;
                });
            }, 1000);
        });
    }
}
