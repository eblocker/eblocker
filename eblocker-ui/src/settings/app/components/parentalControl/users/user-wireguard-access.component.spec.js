/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License.
 */
/* global jasmine */
import 'angular-mocks';

describe('App settings; user WireGuard access component controller', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let $componentController;
    let $compile;
    let $q;
    let $rootScope;
    let $httpBackend;
    let WireGuardService;
    let UserService;
    let NotificationService;

    beforeEach(angular.mock.module(function(
        $provide,
        $translateProvider) {

        WireGuardService = {
            setUserAuthorization:
                jasmine.createSpy('setUserAuthorization')
        };

        UserService = {
            getAll: jasmine.createSpy('getAll'),
            invalidateCache: jasmine.createSpy('invalidateCache')
        };

        NotificationService = {
            error: jasmine.createSpy('error')
        };

        $provide.value('WireGuardService', WireGuardService);
        $provide.value('UserService', UserService);
        $provide.value('NotificationService', NotificationService);
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(
        _$componentController_,
        _$compile_,
        _$q_,
        _$rootScope_,
        _$httpBackend_) {

        $componentController = _$componentController_;
        $compile = _$compile_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;

        $httpBackend.whenGET('/api/adminconsole/console/ip')
            .respond(200, 'http://127.0.0.1');

        $httpBackend.whenGET('/api/adminconsole/systemstatus')
            .respond(200, {
                executionState: 'RUNNING'
            });

        $httpBackend.whenGET('/api/settings')
            .respond(200, {});
    }));

    function realUser(enabled) {
        return {
            id: 7,
            name: 'Parent',
            system: false,
            wireGuardEnabled: enabled
        };
    }

    function create(user) {
        return $componentController(
            'userWireGuardAccessComponent',
            {
                $q: $q,
                WireGuardService: WireGuardService,
                UserService: UserService,
                NotificationService: NotificationService
            },
            {
                user: user
            }
        );
    }

    it('loads the current user permission from a fresh user read', function() {
        const user = realUser(false);

        UserService.getAll.and.returnValue(
            $q.when({
                data: [
                    realUser(true)
                ]
            })
        );

        const ctrl = create(user);
        ctrl.$onInit();
        $rootScope.$digest();

        expect(UserService.getAll).toHaveBeenCalledWith(true);
        expect(ctrl.isApplicable).toBe(true);
        expect(ctrl.enabled).toBe(true);
        expect(user.wireGuardEnabled).toBe(true);
        expect(ctrl.isLoading).toBe(false);
    });

    it('instantiates through the DOM element used by the user details template', function() {
        const user = realUser(true);
        const scope = $rootScope.$new();

        scope.user = user;

        UserService.getAll.and.returnValue(
            $q.when({
                data: [user]
            })
        );

        const element = $compile(
            '<user-wire-guard-access-component ' +
            'user="user"></user-wire-guard-access-component>'
        )(scope);

        $rootScope.$digest();

        const component = element.controller(
            'userWireGuardAccessComponent'
        );

        expect(element.html().trim().length).toBeGreaterThan(0);
        expect(component).toBeDefined();
        expect(component.isApplicable).toBe(true);
        expect(component.enabled).toBe(true);
        expect(element[0].querySelector('md-switch')).not.toBeNull();
    });

    it('handles a fresh user read failure without rejecting from the UI lifecycle', function() {
        const user = realUser(true);

        UserService.getAll.and.callFake(function() {
            return $q.reject({
                status: 500
            });
        });

        const ctrl = create(user);

        let resolved;
        ctrl.$onInit().then(function(value) {
            resolved = value;
        });
        $rootScope.$digest();

        expect(resolved).toBeUndefined();
        expect(ctrl.enabled).toBeUndefined();
        expect(user.wireGuardEnabled).toBeUndefined();
        expect(NotificationService.error).toHaveBeenCalled();
        expect(ctrl.isLoading).toBe(false);
    });

    it('changes only the dedicated WireGuard user authorization', function() {
        const user = realUser(true);

        UserService.getAll.and.returnValue(
            $q.when({
                data: [
                    realUser(true)
                ]
            })
        );

        WireGuardService.setUserAuthorization.and.returnValue(
            $q.when({
                data: false
            })
        );

        const ctrl = create(user);
        ctrl.$onInit();
        $rootScope.$digest();

        ctrl.enabled = false;
        ctrl.setAccess();
        $rootScope.$digest();

        expect(WireGuardService.setUserAuthorization)
            .toHaveBeenCalledWith(7, false);
        expect(ctrl.enabled).toBe(false);
        expect(user.wireGuardEnabled).toBe(false);
        expect(UserService.invalidateCache).toHaveBeenCalled();
        expect(ctrl.isUpdating).toBe(false);
    });

    it('re-reads the unchanged backend permission when authorization update fails', function() {
        const user = realUser(true);

        UserService.getAll.and.returnValue(
            $q.when({
                data: [
                    realUser(true)
                ]
            })
        );

        WireGuardService.setUserAuthorization.and.callFake(function() {
            return $q.reject({
                status: 500
            });
        });

        const ctrl = create(user);
        ctrl.$onInit();
        $rootScope.$digest();

        ctrl.enabled = false;

        let resolved;
        ctrl.setAccess().then(function(value) {
            resolved = value;
        });
        $rootScope.$digest();

        expect(resolved).toBe(true);
        expect(ctrl.enabled).toBe(true);
        expect(user.wireGuardEnabled).toBe(true);
        expect(NotificationService.error).toHaveBeenCalled();
        expect(UserService.invalidateCache).toHaveBeenCalled();
        expect(UserService.getAll.calls.count()).toBe(2);
        expect(ctrl.isUpdating).toBe(false);
    });

    it('is hidden and non-writable for system users', function() {
        const user = {
            id: 1,
            name: 'System',
            system: true,
            wireGuardEnabled: true
        };

        const ctrl = create(user);
        ctrl.$onInit();
        $rootScope.$digest();

        expect(ctrl.isApplicable).toBe(false);
        expect(UserService.getAll).not.toHaveBeenCalled();
        expect(WireGuardService.setUserAuthorization)
            .not.toHaveBeenCalled();
    });
    it('shows unknown after a persisted write errors until the fresh read confirms revocation', function() {
        const user = realUser(true);
        UserService.getAll.and.returnValue($q.when({data: [realUser(true)]}));
        const ctrl = create(user);
        ctrl.$onInit();
        $rootScope.$digest();
        const refresh = $q.defer();
        UserService.getAll.and.returnValue(refresh.promise);
        WireGuardService.setUserAuthorization.and.returnValue(
            $q.reject({status: 500, data: 'Persisted, but runtime reconciliation failed'})
        );
        ctrl.enabled = false;
        ctrl.setAccess();
        $rootScope.$digest();
        expect(ctrl.enabled).toBeUndefined();
        expect(user.wireGuardEnabled).toBeUndefined();
        expect(ctrl.isLoading).toBe(true);
        expect(ctrl.isUpdating).toBe(true);
        refresh.resolve({data: [realUser(false)]});
        $rootScope.$digest();
        expect(ctrl.enabled).toBe(false);
        expect(user.wireGuardEnabled).toBe(false);
        expect(ctrl.isLoading).toBe(false);
        expect(ctrl.isUpdating).toBe(false);
    });

    it('does not resurrect the old user grant when the recovery read also fails', function() {
        const user = realUser(true);
        UserService.getAll.and.returnValue($q.when({data: [realUser(true)]}));
        const ctrl = create(user);
        ctrl.$onInit();
        $rootScope.$digest();
        UserService.getAll.and.returnValue($q.reject({status: 503}));
        WireGuardService.setUserAuthorization.and.returnValue($q.reject({status: 500}));
        ctrl.enabled = false;
        ctrl.setAccess();
        $rootScope.$digest();
        expect(ctrl.enabled).toBeUndefined();
        expect(user.wireGuardEnabled).toBeUndefined();
        expect(ctrl.isUpdating).toBe(false);
        expect(NotificationService.error.calls.count()).toBe(2);
    });

});
