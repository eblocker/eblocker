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
describe('Dashboard Main controller', function() { // jshint ignore: line
    beforeEach(angular.mock.module('eblocker.dashboard'));

    let ctrl, $componentController, mockPauseCard, mockSslCard, mockOnlineCard, mockDeviceCard, $httpBackend;

    const mockLocale = {
        language: 'en'
    };

    const mockDataService = {
        on: angular.noop,
        off: angular.noop
    };

    const mockCardAvailabilityService = {
        filterAndUpdateCards: function(cards) { return angular.copy(cards);}
    };

    const mockWindowService = {
        innerWidth: 1300,
        addEventListener: function(type, handle) {
            // ignore this for mocking
        }
    };

    const mockProductInfo = {
        productFeatures: ['WOL', 'BAS', 'PRO', 'FAM']
    };

    const mockWindow = {
        navigator: {
            userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10.13; rv:61.0) Gecko/20100101 Firefox/61.0'
        }
    };

    const mockCardService = {
        getFilterCards: function () {
            return [mockPauseCard, mockSslCard, mockOnlineCard, mockDeviceCard];
        }
    };

    beforeEach(angular.mock.module(function($provide) {
        $provide.value('locale', mockLocale);
        $provide.value('$window', mockWindowService);
        $provide.value('productInfo', mockProductInfo);
        $provide.value('DataService', mockDataService);
        $provide.value('CardAvailabilityService', mockCardAvailabilityService);
        $provide.value('$window', mockWindow);
        $provide.value('CardService', mockCardService);
    }));

    beforeEach(inject(function($rootScope, $controller, _$componentController_, _$httpBackend_) {
        mockPauseCard = {
            id: 1,
            input: '<dashboard-pause></dashboard-pause>',
            defaultPos: {
                1: {
                    column: 1,
                    order: 1
                },
                2: {
                    column: 1,
                    order: 1
                },
                3: {
                    column: 1,
                    order: 2
                }
            }
        };

        mockSslCard = {
            id: 2,
            input: '<dashboard-ssl></dashboard-ssl>',
            defaultPos: {
                1: {
                    column: 1,
                    order: 2
                },
                2: {
                    column: 1,
                    order: 2
                },
                3: {
                    column: 1,
                    order: 1
                }
            }
        };

        mockOnlineCard = {
            id: 3,
            input: '<dashboard-online></dashboard-online>',
            defaultPos: {
                1: {
                    column: 1,
                    order: 3
                },
                2: {
                    column: 2,
                    order: 1
                },
                3: {
                    column: 2,
                    order: 1
                }
            }
        };

        mockDeviceCard = {
            id: 4,
            input: '<dashboard-device></dashboard-device>',
            defaultPos: {
                1: {
                    column: 1,
                    order: 4
                },
                2: {
                    column: 2,
                    order: 2
                },
                3: {
                    column: 3,
                    order: 1
                }
            }
        };

        // $httpBackend = _$httpBackend_;
        //
        // const cards = [mockPauseCard, mockSslCard, mockOnlineCard, mockDeviceCard];
        // $httpBackend.when('GET', '/api/dashboardcard').respond(200, cards);
        // $httpBackend.when('GET', '/locale/lang-dashboard-en.json').respond(200, {});
        // $httpBackend.when('GET', ' /locale/lang-shared-en.json').respond(200, {});


        $componentController = _$componentController_;
        ctrl = $componentController('mainComponent', {}, {});
        // $httpBackend.flush();
    }));

    describe('initially', function() {
        it('should create a controller instance', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });
    });

    /*
     * We have an overall number of 'numColumns',
     * the card is placed in column 'columnIndex',
     * the card is placed at 'order' within the column.
     */
    function getPositionObject(numColumns, columnIndex, order) {
        const obj = {};
        obj[numColumns] = {
            column: columnIndex,
            order: order
        };
        return obj;
    }
});
