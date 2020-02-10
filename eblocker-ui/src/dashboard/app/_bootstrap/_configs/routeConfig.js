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
export default function AppRouter($stateProvider, $urlRouterProvider) {
    'ngInject';
    'use strict';

    // need default path to main state, so that main is always loaded
    // (also required for scrolling with anchor)
    $urlRouterProvider.otherwise('/main');

    const appState = {
        name: 'app',
        abstract: true,
        params: {anchor: null},
        component: 'dashboardComponent',
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            /*
             * to avoid flash of untranslated content: returns a call to $translate.onReady, effectively blocking the
             * rendering of the app until the first translation file is loaded:
             */
            translateReady: ['$translate', function($translate) {
                return $translate.onReady();
            }],
            security: 'security',
            token: ['security', 'APP_CONTEXT', function(security, APP_CONTEXT) {
                return security.requestToken(APP_CONTEXT.name);
            }],
            consoleUrl: ['RedirectService', function(RedirectService) {
                return RedirectService.prepare();
            }],
            productInfo: ['token', 'registration', function(token, registration) {
                // 'token' needed only indirectly for the REST call
                return registration.loadProductInfo().then(function success() {
                    return registration.getProductInfo();
                });
            }],
            device: ['token', 'DeviceService', function(token, DeviceService) {
                // 'token' needed only indirectly for the REST call
                return DeviceService.getDevice().then(function success(response) {
                    return response.data;
                });
            }],
            operatingUser: ['token', 'device', 'UserService', function(token, device, UserService) {
                // 'token' needed only indirectly for the REST call
                return UserService.getUsers(true).then(function success(response) {
                    if (angular.isObject(device)) {
                        return UserService.getUserById(device.operatingUser);
                    }
                    return null;
                });
            }]
        }
    };

    // redirect app
    const redirectState = {
        name: 'redirect',
        parent: appState.name,
        component: 'redirectComponent'
        // url: '/redirect/:txid/:originalDomain/:targetDomain'
    };

    const redirectOptions = {
        component: 'redirectOptionsComponent',
        name: 'redirectOptions',
        parent: redirectState.name,
        url: '/redirect/:txid/:originalDomain/:targetDomain'
        // url: '/redirect'
    };

    const blockOptions = {
        component: 'blockOptionsComponent',
        name: 'blockOptions',
        parent: redirectState.name,
        url: '/redirect/:txid/:originalDomain'
        // url: '/blocked'
    };

    // "squid-error" pages
    const blockerState = {
        name: 'blocker',
        parent: appState.name,
        component: 'blockerComponent',
        params: {
            appErrorCode: null,
            category: null,
            domain: null,
            error: null,
            externalAclMessage: null,
            listId: null,
            malware: null,
            profileId: null,
            restrictions: null,
            target: null,
            token: null,
            userId: null
        },
        url: '/blocked/:type?' +
        'appErrorCode&category&' +
        'domain&' +
        'error&' +
        'errorDetails&' +
        'externalAclMessage&' +
        'listId&' +
        'malware&' +
        'profileId&' +
        'restrictions&' +
        'target&' +
        'token&' +
        'userId'
    };

    const blockerAccessDenied = {
        component: 'accessDeniedComponent',
        name: 'blockerAccessDenied',
        parent: blockerState.name,
        url: '/access-denied'
    };

    const blockerAdsTrackers = {
        component: 'blockedAdsTrackersComponent',
        name: 'blockerAdsTrackers',
        parent: blockerState.name,
        url: '/blocked-ads-trackers'
    };

    const blockerMalware = {
        component: 'blockedMalwareComponent',
        name: 'blockerMalware',
        parent: blockerState.name,
        url: '/blocked-malware'
    };

    const blockerWhitelisted = {
        component: 'blockerWhitelisted',
        name: 'blockerWhitelisted',
        parent: blockerState.name,
        url: '/blocked-whitelisted'
    };

    const squidError = {
        component: 'squidErrorComponent',
        name: 'squidError',
        parent: blockerState.name,
        url: '/squid-error'
    };

    const blockerSslWhitelisted = {
        component: 'sslWhitelistedComponent',
        name: 'blockerSslWhitelisted',
        parent: blockerState.name,
        url: '/blocked-ssl-whitelisted'
    };

    const consoleState = {
        name : 'console',
        url: '/console',
        controller: 'ConsoleRedirectController',
        controllerAs: 'console',
        resolve: {
            consoleUrl: ['RedirectService', function(RedirectService) {
                return RedirectService.prepare();
            }]
        }
    };

    // allows to go to any state via URL or to scroll to a card on the dashboard
    const actionState = {
        name: 'action',
        url: '/action/:action/:urlToken',
        controller: 'ActionController',
        controllerAs: 'action'
    };

    /*
     * The actual dashboard with cards
     */
    const mainState = {
        component: 'mainComponent',
        name: 'main',
        parent: appState.name,
        resolvePolicy: { async: 'WAIT', when: 'EAGER' },
        resolve: {
            initCards: ['token', 'CardService', 'registration', function(token, CardService, registration) {
                return registration.loadProductInfo().then(function() {
                    const productInfo = registration.getRegistrationInfo().productInfo;
                    return CardService.getDashboardData(true, productInfo);
                });
                // 'token' needed only indirectly for the REST call

            }]
        },
        url: '/main' // need URL for anchor scrolling, so that main state is loaded
    };

    const mobileWizard = {
        component: 'mobileSetupWizardComponent',
        name: 'mobile',
        parent: appState.name,
        url: '/mobileSetup'
    };

    const httpsWizard = {
        component: 'httpsWizardComponent',
        name: 'https',
        parent: appState.name,
        url: '/httpsguide'
    };

    $stateProvider.state(actionState);
    $stateProvider.state(mainState);
    $stateProvider.state(appState);
    $stateProvider.state(consoleState);
    $stateProvider.state(mobileWizard);
    $stateProvider.state(httpsWizard);
    $stateProvider.state(blockerState);
    $stateProvider.state(blockerAccessDenied);
    $stateProvider.state(blockerAdsTrackers);
    $stateProvider.state(blockerMalware);
    $stateProvider.state(blockerWhitelisted);
    $stateProvider.state(squidError);
    $stateProvider.state(blockerSslWhitelisted);
    $stateProvider.state(redirectState);
    $stateProvider.state(redirectOptions);
    $stateProvider.state(blockOptions);
}
