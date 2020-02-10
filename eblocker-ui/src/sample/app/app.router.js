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
export default function AppRouter($stateProvider) {
    'ngInject';
    'use strict';

    /*
     * Splash screen
     */
    var appState = {
        'name': 'app',
        'url': '/',
        'templateUrl': 'app/app.html',
        'controller': 'AppController',
        'controllerAs': 'app'
    };

    /*
     * Main screen with resolved bootstrap dependencies
     */
    var mainState = {
        'name': 'app.main', // must be child of appState
        'templateUrl': 'app/main.html',
        'controller': 'MainController',
        'controllerAs': 'main',
        'resolve': {
            'security': 'security',
            'token': ['security', 'APP_CONTEXT', function(security, APP_CONTEXT) {
                return security.requestToken(APP_CONTEXT.name);
            }],
            'settings': 'settings',
            'locale': ['settings', function(settings) {
                return settings.load();
            }]
        }
    };

    /*
     * Splash screen
     */
    var expiredState = {
        'name': 'app.expired',
        'templateUrl': 'app/common/expired.html',
        'controller': 'ExpiredController',
        'controllerAs': 'expired'
    };

    var redirectNoTargetState = {
        'name': 'redirectNoTarget',
        'url': '/redirect/:txid/:originalDomain',
        'templateUrl': 'app/redirect/redirectNoTarget.html',
        'controller': 'RedirectController',
        'controllerAs': 'redirect'
    };

    var redirectTargetState = {
        'name': 'redirectTarget',
        'url': '/redirect/:txid/:originalDomain/:targetDomain',
        'templateUrl': 'app/redirect/redirectTarget.html',
        'controller': 'RedirectController',
        'controllerAs': 'redirect'
    };

    $stateProvider.state(redirectNoTargetState);
    $stateProvider.state(redirectTargetState);

    $stateProvider.state(expiredState);
    $stateProvider.state(mainState);
    $stateProvider.state(appState);
}
