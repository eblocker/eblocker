/*
 * Copyright 2026 eBlocker Open Source UG (haftungsbeschraenkt)
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

describe('App settings; VPN connection edit dialog controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    var $controller, $httpBackend, $mdDialog, ConsoleService, StateService, SystemService;

    function resolved(response) {
        return {
            then: function(success) {
                if (angular.isFunction(success)) {
                    success(response);
                }
                return resolved(response);
            },
            finally: function(callback) {
                if (angular.isFunction(callback)) {
                    callback();
                }
                return resolved(response);
            }
        };
    }

    StateService = {
        goToState: function() {},
        getWorkflowState: function() {},
        setStates: function() {},
        isStateValid: function() {
            return true;
        },
        getSubStates: function() {
            return [];
        }
    };

    ConsoleService = {
        init: function() {
            return resolved({});
        },
        isInitialized: function() {
            return true;
        },
        initiallyShowNavBar: function() {
            return false;
        },
        isGlobalSpinner: function() {
            return false;
        },
        isPageSpinner: function() {
            return false;
        }
    };

    SystemService = {
        loadSystemStatus: function() {
            return resolved({});
        }
    };

    $mdDialog = {
        hide: function() {},
        cancel: function() {}
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('ConsoleService', ConsoleService);
        $provide.value('StateService', StateService);
        $provide.value('SystemService', SystemService);
        $provide.value('$mdDialog', $mdDialog);
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$controller_, _$httpBackend_) {
        $controller = _$controller_;
        $httpBackend = _$httpBackend_;
        $httpBackend.whenGET('/api/settings').respond(200, {});
    }));

    function createController(dialog) {
        return $controller('VpnConnectionEditController', {
            dialog: dialog
        });
    }

    it('opens for WireGuard provider profiles without legacy login credentials', function() {
        var dialog = {
            isProfileNew: true,
            step: 0,
            profile: {
                id: 23,
                enabled: true,
                temporary: true
            },
            parsedOptions: null,
            requiredFileError: {}
        };
        var ctrl;

        expect(function() {
            ctrl = createController(dialog);
        }).not.toThrow();
        expect(ctrl.dialog.profile.loginCredentials).toEqual({});
        expect(ctrl.getPasswordFieldType()).toBe('text');
    });
});
