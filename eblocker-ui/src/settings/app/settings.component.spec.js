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
import 'angular-mocks';

/* global spyOn */

describe('App settings; Settings component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let ctrl, scope, STATES, StateService, security, Idle, mdDialog, adminConsoleSettings;

    StateService = {
        goToState: angular.noop,
        getActiveState: function() { return {}; }
    };

    security = {
        isPasswordRequired: function() { return true; },
        isAuthenticated: function() { return true; },
        logout: angular.noop,
        renewToken: function() {
            return {
                then: angular.noop
            };
        },
        getSettings: function() {
            return {
                then: function(success) {
                    success({data: adminConsoleSettings});
                }
            };
        },
        storeSecurityContext: angular.noop
    };

    Idle = {
        interrupt: angular.noop,
        watch: angular.noop,
        setIdle: angular.noop,
        setTimeout: angular.noop
    };

    mdDialog = {
        show: angular.noop,
        cancel: angular.noop
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('StateService', StateService);
        $provide.value('security', security);
        $provide.value('Idle', Idle);
        $provide.value('Title', { setEnabled: angular.noop });
        $provide.value('ConsoleService', {
            isGlobalSpinner: function() { return false; },
            goToDashboard: angular.noop,
            goToStaticHelp: angular.noop,
            showDashboardButton: function() { return true; }
        });
        $provide.value('SplashService', {});
        $provide.value('$mdDialog', mdDialog);
        $provide.value('$mdSidenav', function() {
            return {
                toggle: angular.noop
            };
        });
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function($rootScope, _$componentController_, _STATES_) {
        scope = $rootScope.$new();
        adminConsoleSettings = {sessionTimeoutSeconds: 1200};
        STATES = _STATES_;
        spyOn(StateService, 'goToState');
        spyOn(security, 'logout');
        ctrl = _$componentController_('settingsComponent', {$scope: scope}, {});
    }));

    describe('initially', function() {
        it('should create a controller instance', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });

    describe('on idle timeout', function() {
        it('should log out and redirect directly to the login screen when a password is required', function() {
            scope.$broadcast('IdleTimeout');

            expect(security.logout).toHaveBeenCalled();
            expect(StateService.goToState).toHaveBeenCalledWith(STATES.AUTH);
            expect(StateService.goToState).not.toHaveBeenCalledWith(STATES.EXPIRED);
        });
    });

    describe('on idle start', function() {
        it('should not show an idle dialog when session timeout is disabled', function() {
            adminConsoleSettings.sessionTimeoutSeconds = 0;
            ctrl.systemStatus = {executionState: 'OK'};
            ctrl.locale = {language: 'en'};
            spyOn(Idle, 'setTimeout');
            spyOn(Idle, 'interrupt');
            spyOn(mdDialog, 'show');

            ctrl.$onInit();
            scope.$broadcast('IdleStart');

            expect(Idle.setTimeout).toHaveBeenCalledWith(false);
            expect(Idle.interrupt).toHaveBeenCalled();
            expect(mdDialog.show).not.toHaveBeenCalled();
        });
    });
});
