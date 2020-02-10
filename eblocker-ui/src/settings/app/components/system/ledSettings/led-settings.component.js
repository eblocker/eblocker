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
    templateUrl: 'app/components/system/ledSettings/led-settings.component.html',
    controller: Controller,
    controllerAs: 'vm'
};

function Controller(logger, LedSettingsService, NotificationService) {
    'ngInject';
    'use strict';

    const vm = this;

    // Default LED settings:
    vm.ledSettings = {brightness: 0.3, hardwareAvailable: false};
    vm.model = vm.ledSettings.brightness;

    vm.sliderUpdated = function() {
        vm.ledSettings.brightness = vm.model;
        LedSettingsService.updateSettings(vm.ledSettings).then(
            function success(response) {
                logger.debug('Updated LED settings');
            },
            function error(response) {
                logger.error('Could not update LED settings');
                NotificationService.error('ADMINCONSOLE.LED_SETTINGS.ERROR');
            });
    };

    LedSettingsService.getSettings().then(
        function success(response) {
            vm.ledSettings = response;
            vm.model = vm.ledSettings.brightness;
        },
        function error(response) {
            logger.error('Could not get LED settings');
            NotificationService.error('ADMINCONSOLE.LED_SETTINGS.ERROR');
        });
}
