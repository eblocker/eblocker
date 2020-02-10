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
describe('Message service', function() {
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    let messageService, $httpBackend;

    let mockMessage1 = {
        id: 1000,
        titleKey: 'MESSAGE_SSL_SUPPORT_INSTALL_TITLE',
        contentKey:	'MESSAGE_SSL_SUPPORT_INSTALL_CONTENT',
        actionButtonLabelKey: 'MESSAGE_SSL_SUPPORT_INSTALL_LABEL',
        actionButtonUrlKey: 'MESSAGE_SSL_SUPPORT_INSTALL_URL',
        titles: {},
        contents: {},
        context: {},
        date: '2017-08-22T08:00:00.000Z',
        showDoNotShowAgain: false,
        messageSeverity: 'INFO'
    };
    let mockMessage2 = {
        id: 1002,
        titleKey: 'MESSAGE_ALERT_EVENT_TITLE',
        contentKey:	'MESSAGE_ALERT_EVENT_CONTENT',
        actionButtonLabelKey: 'MESSAGE_ALERT_EVENT_LABEL',
        actionButtonUrlKey: 'MESSAGE_ALERT_EVENT_URL',
        titles: {},
        contents: {},
        context: {},
        date: '2017-08-22T07:00:00.000Z',
        showDoNotShowAgain: false,
        messageSeverity: 'INFO'
    };
    let mockMessage3 = {
        id: 1005,
        titleKey: 'MESSAGE_CERTIFICATE_UNTRUSTED_TITLE',
        contentKey:	'MESSAGE_CERTIFICATE_UNTRUSTED_CONTENT',
        actionButtonLabelKey: 'MESSAGE_CERTIFICATE_UNTRUSTED_LABEL',
        actionButtonUrlKey: 'MESSAGE_CERTIFICATE_UNTRUSTED_URL',
        titles: {},
        contents: {},
        context: {},
        date: '2017-08-22T09:00:00.000Z',
        showDoNotShowAgain: false,
        messageSeverity: 'ALERT'
    };

    let mockMessages = [mockMessage1, mockMessage2, mockMessage3];

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $provide.value('$translate', {
            preferredLanguage: function() {
                return 'en';
            },
            storage: function() {
                return undefined;
            },
            storageKey: function() {
                return 'NG_TRANSLATE_LANG_KEY';
            },
            use: function() {
                return 'en';
            }
        });
        $provide.factory('customLoader', function ($q) {
            return function () {
                let deferred = $q.defer();
                deferred.resolve({});
                return deferred.promise;
            };
        });
        $translateProvider.useLoader('customLoader');
    }));

    beforeEach(inject(function(_$httpBackend_, _MessageService_) {
        messageService = _MessageService_;
        $httpBackend = _$httpBackend_;
    }));

    describe('test getMessages', function() {
        it('should return the messages that are received from the backend', function() {
            $httpBackend.when('GET', '/api/messages').respond(200, mockMessages);
            messageService.getMessages().then(function(messages) {
                expect(messages.length).toEqual(mockMessages.length);
            });
            $httpBackend.flush();
        });
    });

    describe('test sortMessagesByDate', function() {
        it('should return the sorted messages list', function() {
            let sorted = messageService.sortMessagesByDate(mockMessages);
            expect(sorted[0]).toEqual(mockMessage2);
            expect(sorted[1]).toEqual(mockMessage1);
            expect(sorted[2]).toEqual(mockMessage3);
        });
    });

    describe('test sortMessagesBySeverity', function() {
        it('should return the sorted messages list', function() {
            let sorted = messageService.sortMessagesBySeverity(mockMessages);
            expect(sorted[2]).toEqual(mockMessage3);
        });
    });

    describe('test updateDoNotShowAgain', function() {
        it('should make a server call', function() {
            $httpBackend.when('PUT', '/api/messages/donotshowagain').respond(200, {});
            messageService.updateDoNotShowAgain(1, true);
            $httpBackend.flush();
        });
    });

    describe('other functions that require an init state of messageService with mock messages', function() {

        beforeEach(function() {
            // init messageService with mock messages
            $httpBackend.when('GET', '/api/messages').respond(200, mockMessages);
            messageService.getMessages().then(function(messages) {
                expect(messages.length).toEqual(mockMessages.length);
            });
            $httpBackend.flush();
        });

        describe('test hideMessage', function() {
            it('should remove the message from the list', function() {
                $httpBackend.when('POST', '/api/messages/hide').respond(200);
                messageService.hideMessage(1000).then(function() {
                    messageService.getMessages().then(function(messages) {
                        expect(messages.length).toBe(mockMessages.length - 1);
                    });
                });
                $httpBackend.flush();
            });
        });

        describe('test executeAction', function() {
            it('should remove the message from the list', function() {
                $httpBackend.when('POST', '/api/messages/action').respond(200);
                messageService.executeAction(1000).then(function() {
                    messageService.getMessages().then(function(messages) {
                        expect(messages.length).toBe(mockMessages.length - 1);
                    });
                });
                $httpBackend.flush();
            });
        });
    });
});
