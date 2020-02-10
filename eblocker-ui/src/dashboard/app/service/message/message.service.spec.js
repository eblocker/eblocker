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
describe('Message service', function() { // jshint ignore: line
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.dashboard.app'));
    beforeEach(angular.mock.module('eblocker.dashboard'));
    // ** Not Sure why these modules have to be included here, since they are
    // already included in 'eblocker.dashboard'.
    // beforeEach(angular.mock.module('eblocker.logger'));
    // beforeEach(angular.mock.module('eblocker.security'));

    let messageService, $httpBackend, mockDataCachingService, $q, $rootScope;

    const mockMessage1 = {
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
    const mockMessage2 = {
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
    const mockMessage3 = {
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

    const mockMessages = [mockMessage1, mockMessage2, mockMessage3];

    mockDataCachingService = {
        loadCache: function() {
            const deferred = $q.defer();
            deferred.resolve({data: mockMessages});
            return deferred.promise;
        }
    };

    const mockRegistrationInfo = {
        loadProductInfo: function() {
            const deferred = $q.defer();
            const response = {
                data: []
            };
            deferred.resolve(response);
            return deferred.promise;
        },
        getProductInfo: function() {},
        getRegistrationInfo: function() {
            return {productInfo: {}};
        }
    };

    const mockCardService = {
        getFilterCards: function () {
            return [];
        },
        getDashboardData: function() {}
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('DataCachingService', mockDataCachingService);
        $provide.value('registration', mockRegistrationInfo);
        $provide.value('CardService', mockCardService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_$httpBackend_, _MessageService_, _$q_, _$rootScope_) {
        $q = _$q_;
        $rootScope = _$rootScope_;
        messageService = _MessageService_;
        $httpBackend = _$httpBackend_;
        $httpBackend.when('GET', '/messages').respond(200, mockMessages);
        $httpBackend.when('GET', '/api/token/DASHBOARD').respond(200, {});
        $httpBackend.when('GET', '/api/settings').respond(200, {});
        $httpBackend.when('GET', '/controlbar/console/ip').respond(200, {});
        $httpBackend.when('GET', '/api/device').respond(200, {});
        $httpBackend.when('GET', '/api/dashboard/users').respond(200, {});
    }));

    describe('test getMessages', function() {
        it('should return the messages that are received from the backend', function() {
            messageService.getMessages().then(function(response) {
                const messages = response.data;
                expect(messages.length).toEqual(mockMessages.length);
            });
        });
    });

    describe('test sortMessagesByDate', function() {
        it('should return the sorted messages list', function() {
            const sorted = messageService.sortMessagesByDate(mockMessages);
            expect(sorted[0]).toEqual(mockMessage2);
            expect(sorted[1]).toEqual(mockMessage1);
            expect(sorted[2]).toEqual(mockMessage3);
        });
    });

    describe('test sortMessagesBySeverity', function() {
        it('should return the sorted messages list', function() {
            const sorted = messageService.sortMessagesBySeverity(mockMessages);
            expect(sorted[2]).toEqual(mockMessage3);
        });
    });

    describe('test updateDoNotShowAgain', function() {
        it('should make a server call', function() {
            $httpBackend.expect('PUT', '/messages/donotshowagain').respond(200, {});
            messageService.updateDoNotShowAgain(1, true);
            $httpBackend.flush();
        });
    });

    describe('other functions that require an init state of messageService with mock messages', function() {

        beforeEach(function() {
            messageService.getMessages().then(function(response) {
                const messages = response.data;
                expect(messages.length).toEqual(mockMessages.length);
            });
            // We have to make angular resolve the promise (WhitelistService), since it is not done
            // automatically because no template is involved:
            // https://stackoverflow.com/questions/24211312/angular-q-when-is-not-resolved-in-karma-unit-test
            $rootScope.$apply();
        });

        describe('test getNumberOfAlerts', function() {
            it('should return the correct number of alert messages', function() {
                expect(messageService.getNumberOfAlerts()).toEqual(1);
            });
        });

        describe('test getNumberOfInfo', function() {
            it('should return the correct number of info messages', function() {
                expect(messageService.getNumberOfInfo()).toEqual(2);
            });
        });

        describe('test hideMessage', function() {
            it('should make a server call', function() {
                $httpBackend.expect('POST', '/messages/hide').respond(200);
                messageService.hideMessage(1000);
                $httpBackend.flush();
            });
        });

        describe('test executeAction', function() {
            it('should make a server call', function() {
                $httpBackend.expect('POST', '/messages/action').respond(200);
                messageService.executeAction(1000);
                $httpBackend.flush();
            });
        });
    });
});
