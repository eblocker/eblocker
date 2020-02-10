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
export default function($urlRouterProvider, $stateProvider) {
    'ngInject';
    $urlRouterProvider.otherwise('/welcome');

    /*
     * Splash screen
     */
    let appState = {
        name: 'app',
        component: 'adviceComponent',
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            security: 'security',
            token: ['security', 'APP_CONTEXT', function(security, APP_CONTEXT) {
                return security.requestToken(APP_CONTEXT.name);
            }],
            settings: 'settings',
            locale: ['settings', function(settings) {
                return settings.load();
            }]
        }
    };

    const welcomeState = {
        name: 'welcome', // must be child of appState
        parent: appState.name,
        component: 'welcomeComponent',
        url: '/welcome/:pageContextId',
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            consoleUrl: ['token', 'ConsoleService', function(token, ConsoleService) {
                return ConsoleService.init();
            }],
            productInfo: ['token', 'RegistrationService', function(token, RegistrationService) {
                // 'token' needed only indirectly for the REST call
                return RegistrationService.getProductInfo();
            }],
            device: ['token', 'DeviceService', function(token, DeviceService) {
                // 'token' needed only indirectly for the REST call
                return DeviceService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return {};
                });
            }]
        }
    };

    const reminderState = {
        name: 'reminder', // must be child of appState
        parent: appState.name,
        component: 'reminderComponent',
        url: '/reminder/:pageContextId',
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            consoleUrl: ['token', 'ConsoleService', function(token, ConsoleService) {
                return ConsoleService.init();
            }],
            productInfo: ['token', 'RegistrationService', function(token, RegistrationService) {
                // 'token' needed only indirectly for the REST call
                return RegistrationService.getProductInfo();
            }],
            device: ['token', 'DeviceService', function(token, DeviceService) {
                // 'token' needed only indirectly for the REST call
                return DeviceService.get().then(function success(response) {
                    return response.data;
                }, function error() {
                    return {};
                });
            }]
        }
    };

    $stateProvider.state(appState);
    $stateProvider.state(welcomeState);
    $stateProvider.state(reminderState);
}
