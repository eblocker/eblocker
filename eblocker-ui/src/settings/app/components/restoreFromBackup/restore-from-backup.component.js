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
export default {
    templateUrl: 'app/components/restoreFromBackup/restore-from-backup.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
    }
};

function Controller(logger, RestoreFromBackupService, NotificationService, SystemService,
                    StateService, STATES) {
    'ngInject';

    const vm = this;
    vm.includeKeys = true;
    vm.currentStep = 0;
    vm.verifying = false;
    vm.importing = false;
    vm.warnings = [];

    vm.isStepAllowed = function(step) {
        // reboot? No turning back...
        if (vm.currentStep === 2) {
            return step === 2;
        }
        // only earlier steps are allowed
        return step <= vm.currentStep;
    };

    vm.updateIncludeKeys = function() {
        if (vm.includeKeys === false) {
            delete vm.password;
        }
    };

    vm.verifyConfigBackup = function() {
        logger.warn('Verifying backup');
        if (!vm.configBackupImportForm.$valid) {
            return;
        }
        vm.passwordRetry = false;
        vm.verifying = true;
        RestoreFromBackupService.verifyConfig(vm.password).then(function(result) {
            vm.warnings = result.warnings;
            vm.currentStep = 1;
        }, function(response) {
            const errCode = response.toUpperCase();
            if (errCode === 'ADMINCONSOLE.CONFIG_BACKUP.ERROR.INVALID_PASSWORD') {
                vm.passwordRetry = true;
            } else {
                NotificationService.error(errCode);
            }
        }).finally(function() {
            vm.verifying = false;
        });
    };

    vm.importConfigBackup = function() {
        vm.importing = true;
        RestoreFromBackupService.importConfig(vm.password).then(function(result) {
            vm.warnings = result.warnings;
            vm.currentStep = 2;
        }, function(response) {
            NotificationService.error(response.toUpperCase());
        }).finally(function() {
            vm.importing = false;
        });
    };

    vm.reboot = function() {
        SystemService.setCurrentProcess('RESTART');
        SystemService.reboot().then(function(response) {
            StateService.goToState(STATES.STAND_BY);
        }, function error(reason) {
            logger.error('Could not reboot eBlocker', reason);
            NotificationService.error('ADMINCONSOLE.STATUS.ERROR.REBOOT', reason);
        });
    };

    vm.cancel = function() {
        // TODO tell the user to remove the stick?
    };

}
