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
export default function MainSwitch(logger, $rootScope, $interval, UserProfileService, UserService, SslService, DnsService, DeviceService, // jshint ignore: line
                                   LocalTimestampService, PauseService, TorService, VpnService, CloakingService,
                                   MessageService, SystemService, WhitelistService, onlineTime, CardService) {
    'ngInject';
    'use strict';

    const DATA_HANDLER_INTERVAL = 3000;
    let running, checkRegisteredComponentsInterval;

    startDataHandleInterval();

    $rootScope.$on('$destroy', stopDataHandleInterval);

    /**
     * Some services must not be shut off, because service needs to update data in background to check if cards
     * needs to be displayed again.
     */
    const services = {
        UserService: {
            service: UserService,
            alwaysRunning: true, // to update toolbar
            params: [10000]// update interval
        },
        UserProfileService: {
            service: UserProfileService,
            alwaysRunning: true, // to hide/display pause card
            params: [10000]// update interval
        },
        SslService: {
            service: SslService,
            alwaysRunning: true, // to hide/display pause card
            params: [10000]// update interval
        },
        CardService: {
            service: CardService,
            alwaysRunning: true, // to update dashboard cards and columns view
            params: [10000]// update interval
        },
        DnsService: {
            service: DnsService,
            params: [10000]// update interval
        },
        DeviceService: {
            service: DeviceService,
            alwaysRunning: true, // to update toolbar
            params: [10000]// update interval
        },
        LocalTimestampService: {
            service: LocalTimestampService,
            params: [10000]// update interval
        },
        PauseService: {
            service: PauseService,
            alwaysRunning: true, // to hide/display pause card
            params: [10000]// update interval
        },
        MessageService: {
            service: MessageService,
            params: [10000]// update interval
        },
        TorService: {
            service: TorService,
            params: [2000]// update interval
        },
        VpnService: {
            service: VpnService,
            params: [2000]// update interval
        },
        CloakingService: {
            service: CloakingService,
            params: [2000]// update interval
        },
        SystemService: {
            service: SystemService,
            alwaysRunning: true, // to notice system status changes (boot, update, etc.)
            params: [4000]// update interval
        },
        WhitelistService: {
            service: WhitelistService,
            params: [10000]// update interval
        },
        onlineTime: {
            service: onlineTime, // The backend updates the time every 30
            // seconds, updating every 10 seconds can
            // lead to the frontend counting down and
            // then having the remaining minutes
            // increased and decreased until the backend
            // is up to date again
            params: [32000]// update interval
        }
    };

    function maintainDataHandler() {
        Object.keys(services).forEach((name) => {
            const service = services[name];

            // if dashboard is paused (var 'running') and service is still running, stop service
            if (((angular.isArray(service.registeredComponents) && service.registeredComponents.length > 0) ||
                service.alwaysRunning) &&
                running) {
                // only start if service is not running yet
                if (!service.running) {
                    logger.debug('Starting service \'' + name + '\'...');
                    service.service.start(service.params[0]);
                    service.running = true;
                }
            } else if (service.running) {
                logger.debug('Stopping service \'' + name + '\'...');
                service.service.stop();
                service.running = false;
            }
        });
    }

    function startDataHandleInterval() {
        checkRegisteredComponentsInterval = $interval(maintainDataHandler, DATA_HANDLER_INTERVAL);
    }

    function stopDataHandleInterval() {
        if (angular.isDefined(checkRegisteredComponentsInterval)) {
            $interval.cancel(checkRegisteredComponentsInterval);
        }
    }

    function registerComponentAsServiceListener(component, service) {
        if (angular.isObject(services[service])) {
            if (angular.isUndefined(services[service].registeredComponents)) {
                services[service].registeredComponents = [component];
            } else {
                services[service].registeredComponents.push(component);
            }
        } else {
            logger.warn('Unknown service for data listener \'' + service + '\' in component ' + component);
        }
    }

    function unregisterComponentAsServiceListener(component, service) {
        const i = services[service].registeredComponents.indexOf(component);
        if (angular.isDefined(services[service].registeredComponents) &&
            i > -1) {
            services[service].registeredComponents.splice(i, 1);
            if (services[service].registeredComponents.length === 0) {
                delete services[service].registeredComponents;
            }
        }
    }

    function startServices() {
        logger.debug('Starting all services...');
        running = true;
        startDataHandleInterval();
    }

    function stopServices() {
        logger.debug('Stopping all services...');
        running = false;
        stopDataHandleInterval();

        Object.keys(services).forEach(function(name) {
            const service = services[name];
            if (service.running) {
                logger.debug('Stopping service \'' + name + '\'...');
                service.service.stop();
                service.running = false;
            }
        });
    }

    function isRunning() {
        return running;
    }

    return {
        on: startServices,
        off: stopServices,
        isRunning: isRunning,
        registerComponentAsServiceListener: registerComponentAsServiceListener,
        unregisterComponentAsServiceListener: unregisterComponentAsServiceListener
    };

}
