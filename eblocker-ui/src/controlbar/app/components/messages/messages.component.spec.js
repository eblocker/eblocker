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

/* jshint expr:true */
describe('App controlbar; Component: Messages', function() {
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    var $componentController, $q, $rootScope, $mdDialog, $httpBackend, ctrl, mockLoggerService, mockMessages,
        mockNotificationService, mockControlbarService;

    mockLoggerService = {
        info: function(param) {
            // nothing to do for now
        },
        error: function(param) {
            // nothing to do for now
        }
    };

    mockNotificationService = {
        info: function() {},
        warning: function() {},
        error: function() {}
    };

    mockControlbarService = {
        showAlertMessagesNotification: function() {}
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('logger', mockLoggerService);
        // $provide.value('NotificationService', mockNotificationService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$componentController_, _$q_, _$rootScope_, _$mdDialog_, _$httpBackend_) {
        $componentController = _$componentController_;
        $q = _$q_;
        $rootScope = _$rootScope_;
        $mdDialog = _$mdDialog_;
        $httpBackend = _$httpBackend_;

        mockMessages = [
            {
                id: 42,
                titleKey: 'MESSAGE_RELEASE_NOTES_TITLE',
                contentKey: 'MESSAGE_RELEASE_NOTES_CONTENT',
                actionButtonLabelKey: 'MESSAGE_RELEASE_NOTES_LABEL',
                actionButtonUrlKey: 'MESSAGE_RELEASE_NOTES_URL',
                titles: {},
                contents: {},
                context: {
                    versionMajorMinor: '1-13',
                    version: '1.13.0-SNAPSHOT'
                },
                date: '2017-12-11T09:39:51.667Z',
                showDoNotShowAgain: true,
                messageSeverity: 'INFO'
            },
            {
                id: 1002,
                titleKey: 'MESSAGE_ALERT_EVENT_TITLE',
                contentKey: 'MESSAGE_ALERT_EVENT_CONTENT',
                actionButtonLabelKey: 'MESSAGE_ALERT_EVENT_LABEL',
                actionButtonUrlKey: 'MESSAGE_ALERT_EVENT_URL',
                titles: {},
                contents: {},
                context: {},
                date: '2017-12-15T14:16:10.872Z',
                showDoNotShowAgain: false,
                messageSeverity: 'ALERT'
            },
            {
                id: 99,
                titleKey: 'MESSAGE_RELEASE_NOTES_TITLE',
                contentKey: 'MESSAGE_RELEASE_NOTES_CONTENT',
                actionButtonLabelKey: 'MESSAGE_RELEASE_NOTES_LABEL',
                actionButtonUrlKey: 'MESSAGE_RELEASE_NOTES_URL',
                titles: {},
                contents: {},
                context: {},
                date: '2017-12-09T09:39:51.667Z',
                showDoNotShowAgain: true,
                messageSeverity: 'INFO'
            }
        ];

        $httpBackend.when('GET', '/api/messages').respond(200, mockMessages);

        ctrl = $componentController('messages', {
            $mdDialog: $mdDialog,
            NotificationService: mockNotificationService,
            ControlbarService: mockControlbarService
        }, {});

        $httpBackend.flush();
    }));

    describe('initially', function() {
        it('controller should be defined', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });

    describe('getMessages', function() {
        it('should sort messages latest first', function() {
            expect(mockMessages[0].id).toEqual(42);
            expect(mockMessages[1].id).toEqual(1002);
            expect(mockMessages[2].id).toEqual(99);
            ctrl.getMessages();
            expect(ctrl.messages[0].id).toEqual(1002);
            expect(ctrl.messages[1].id).toEqual(42);
            expect(ctrl.messages[2].id).toEqual(99);
        });

        it('should notify user about important messages', function() {
            /* jshint ignore:start */
            spyOn(mockNotificationService, 'warning').and.callThrough(); //.and.callThrough();
            spyOn(mockControlbarService, 'showAlertMessagesNotification').and.callThrough(); //.and.callThrough();
            ctrl.getMessages().then(function() {
                expect(mockNotificationService.warning).toHaveBeenCalled();
                expect(mockControlbarService.showAlertMessagesNotification).toHaveBeenCalledWith(false);
            });
            /* jshint ignore:end */
        });
    });
});
