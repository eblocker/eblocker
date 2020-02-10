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
export default function MobileConfirmCloseController(logger, $mdDialog, msgKeys, close, save, subject,
                                                     validateSubject) {
    'ngInject';

    const vm = this;

    vm.msgKeys = msgKeys;

    vm.errors = [];

    vm.cancel = function() {
        $mdDialog.cancel();
    };

    vm.saveAndClose = function() {

        vm.errors = validateSubject(subject);
        if (vm.errors.length > 0) {
            logger.warn('There are validation errors for the subject.');
            return;
        }
        save(subject).then(function success() {
            vm.close();
        });
    };

    vm.close = function() {
        close(subject).then(function success() {
            $mdDialog.hide(subject);
        }, function(data) {
            logger.error('Error executing ok action for mobile confirm dialog ', data);
        });
    };
}
