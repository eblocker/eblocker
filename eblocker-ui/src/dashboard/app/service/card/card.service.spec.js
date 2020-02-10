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
// jshint ignore: line
describe('App: dashboard; CardService', function() { // jshint ignore: line
    beforeEach(angular.mock.module('template.dashboard.app'));
    beforeEach(angular.mock.module('eblocker.dashboard'));

    let cardService, $q, $rootScope, $httpBackend,
        mockPauseCard, mockSslCard, mockOnlineCard, mockUserCard, userColumns, allCards, testScreenRes;

    const mockDataCachingService = {
        loadCache: function(cache, path) {
            let ret;
            if (path === '/api/dashboardcard') {
                ret = allCards;
            } else if (path === '/api/dashboardcard/columns') {
                ret = userColumns;
            }
            const deferred = $q.defer();
            const response = {
                data: ret
            };
            deferred.resolve(response);
            return deferred.promise;
        }
    };

    const mockCardAvailabilityService = {
        updateData: function() {
            const deferred = $q.defer();
            const response = {
                data: []
            };
            deferred.resolve(response);
            return deferred.promise;
        },
        isCardAvailable: function() {
            return true;
        },
        onlyShowInDropdown: function() {
            return false;
        },
        getTooltipForDisableState: function() {
            return undefined;
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

    const mockResolutionService = {
        getScreenSize: function() {
            return testScreenRes;
        }
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('registration', mockRegistrationInfo);
        $provide.value('DataCachingService', mockDataCachingService);
        $provide.value('CardAvailabilityService', mockCardAvailabilityService);
        $provide.value('ResolutionService', mockResolutionService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_CardService_, _$q_, _$rootScope_, _$httpBackend_) {
        $q = _$q_;
        $rootScope = _$rootScope_;
        $httpBackend = _$httpBackend_;

        userColumns = {
            oneColumn: [
                {id: 1, column: 1, index: 1, visible: true},
                {id: 2, column: 1, index: 2, visible: true},
                {id: 3, column: 1, index: 3, visible: true},
                {id: 4, column: 1, index: 4, visible: true}
                ],
            twoColumn: [
                {id: 1, column: 1, index: 1, visible: true},
                {id: 2, column: 1, index: 2, visible: true},
                {id: 3, column: 2, index: 1, visible: true},
                {id: 4, column: 2, index: 2, visible: true}
            ],
            threeColumn: [
                {id: 1, column: 1, index: 2, visible: true},
                {id: 2, column: 1, index: 1, visible: true},
                {id: 3, column: 2, index: 1, visible: true},
                {id: 4, column: 3, index: 1, visible: true}
            ]
        };

        mockPauseCard = {
            id: 1,
            name: 'PAUSE'
        };

        mockSslCard = {
            id: 2,
            name: 'SSL'
        };

        mockOnlineCard = {
            id: 3,
            name: 'ONLINE_TIME'
        };

        mockUserCard = {
            id: 4,
            name: 'USER'
        };
        cardService = _CardService_;
    }));

    beforeEach(function() {
        $httpBackend.when('GET', '/api/token/DASHBOARD').respond(200, {});
        $httpBackend.when('GET', '/api/settings').respond(200, {});
        $httpBackend.when('GET', '/controlbar/console/ip').respond(200, {});
        $httpBackend.when('GET', '/api/device').respond(200, {});
        $httpBackend.when('GET', '/api/dashboard/users').respond(200, {});

        allCards = [mockUserCard, mockSslCard, mockPauseCard, mockOnlineCard];
        const mockProductInfo = {
            productFeatures: ['WOL', 'BAS', 'PRO', 'FAM']
        };
        cardService.getDashboardData(true, mockProductInfo);
        $rootScope.$apply();
        $httpBackend.flush();
    });

    describe('initially', function() {
        it('should create a service instance', function() {
            expect(angular.isDefined(cardService)).toBe(true);
        });
    });

    describe('test rearrangeDashboardCards for large screens (width >= 1280px)', function() {
        beforeEach(function () {
            testScreenRes = 'lg';
        });

        it('should create three columns', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns.length).toEqual(3);
        });

        it('should arrange columns according to default position', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns[0].length).toEqual(2); // mockPauseCard and mockSslCard
            expect(columns[1].length).toEqual(1); // mockOnlineCard
            expect(columns[2].length).toEqual(1); // mockDeviceCard
        });
    });

    describe('test rearrangeDashboardCards for medium screens (width >= 960px AND width < 1280px)', function() {
        beforeEach(function () {
            testScreenRes = 'md';
        });

        it('should create two columns', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns.length).toEqual(2);
        });

        it('should arrange columns according to default position', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns[0].length).toEqual(2); // mockPauseCard and mockSslCard
            expect(columns[1].length).toEqual(2); // mockOnlineCard
        });
    });

    describe('test rearrangeDashboardCards for small screens (width >= 680px AND width < 960px)', function() {
        beforeEach(function () {
            testScreenRes = 'mdsm';
        });

        it('should create one column', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns.length).toEqual(1);
        });

        it('should arrange columns according to default position', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns[0].length).toEqual(4); // mockPauseCard and mockSslCard
        });

    });

    describe('test rearrangeDashboardCards for even smaller screens (width >= 550px AND width < 680px)', function() {
        beforeEach(function () {
            testScreenRes = 'sm';
        });

        it('should create one column', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns.length).toEqual(1);
        });

        it('should arrange columns according to default position', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns[0].length).toEqual(4); // mockPauseCard and mockSslCard
        });
    });

    describe('test rearrangeDashboardCards for tiny screens (width < 550px)', function() {
        beforeEach(function () {
            testScreenRes = 'xs';
        });

        it('should create one column', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns.length).toEqual(1);
        });

        it('should arrange columns according to default position', function() {
            const columns = cardService.getCardsByColumns();
            expect(columns[0].length).toEqual(4); // mockPauseCard and mockSslCard
        });
    });

    describe('test checkForCollapsedCard', function() { // jshint ignore: line
        it('should return true if one card is not expanded', function() {
            const columns = [
                {expanded: true},
                {expanded: false},
                {expanded: true}
                ];
            expect(cardService.checkForCollapsedCard(columns)).toBe(true);
        });

        it('should return true if all cards are not expanded', function() {
            const columns = [
                {expanded: false},
                {expanded: false},
                {expanded: false}
            ];
            expect(cardService.checkForCollapsedCard(columns)).toBe(true);
        });

        it('should return false if no card is not expanded', function() {
            const columns = [
                {expanded: true},
                {expanded: true},
                {expanded: true}
            ];
            expect(cardService.checkForCollapsedCard(columns)).toBe(false);
        });
    });
});
