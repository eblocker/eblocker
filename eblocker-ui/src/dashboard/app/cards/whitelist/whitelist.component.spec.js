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
/* jshint expr:true */
describe('Component: dashboardWhitelist', function() { // jshint ignore: line
    // ** template cache: so that calls for templates get the 'real' HTML file.
    beforeEach(angular.mock.module('template.dashboard.app'));
    beforeEach(angular.mock.module('eblocker.dashboard'));

    let $componentController,
        $httpBackend,
        $q,
        $rootScope,
        ctrl,
        mockWhitelist,
        mockWhitelistService,
        mockDeviceService;


    const mockDataService = {
        registerComponentAsServiceListener: function() {},
        unregisterComponentAsServiceListener: function() {}
    };

    // TODO 'md-card-content' should in config file
    // Because it can be used in all cards and
    // may change when the directive changes
    const utilsHtmlString = 'md-card-content';

    mockWhitelistService = {
        getWhitelist: function() {
            const deferred = $q.defer();
            const response = {
                data: mockWhitelist
            };
            deferred.resolve(response);
            return deferred.promise;
        },
        setWhitelist: function(whitelist) {
            const deferred = $q.defer();
            mockWhitelist = {};
            whitelist.forEach(function(each) {
                mockWhitelist[each.domain] = {
                    trackers: each.trackers,
                    ads: each.ads
                };
            });
            deferred.resolve();
            return deferred.promise;
        },
        updateWhitelistEntry: function(entry) {
            const deferred = $q.defer();
            deferred.resolve();
            return deferred.promise;
        },
        getWhitelistConfig: function() {
            const deferred = $q.defer();
            const response = {
                data: {
                    blockTrackings: true,
                    blockAds: true
                }
            };
            deferred.resolve(response);
            return deferred.promise;
        }
    };

    const mockDevice = {
        filterMode: 'ADVANCED'
    };

    mockDeviceService = {
        getDevice: function() {
            const deferred = $q.defer();
            deferred.resolve(mockDevice);
            return deferred.promise;
        },
        update: function() {

        }
    };

    const mockReg = {
        loadProductInfo: function () {
            return '';
        }
    };

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        $provide.value('CardService', {});
        $provide.value('registration', mockReg);
        $provide.value('WhitelistService', mockWhitelistService);
        $provide.value('DeviceService', mockDeviceService);
        $provide.value('DataService', mockDataService);
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $provide.factory('customLoader', function ($q) {
            return function () {
                const deferred = $q.defer();
                deferred.resolve({});
                return deferred.promise;
            };
        });
        $translateProvider.useLoader('customLoader');
    }));

    beforeEach(inject(function(_$componentController_, _$httpBackend_, _$q_, _$rootScope_) {
        mockWhitelist = {
            'B eblocker.com': {
                ads: true,
                trackers: true
            },
            'C eblocker.de': {
                ads: false,
                trackers: true
            },
            'A eblocker.org': {
                ads: true,
                trackers: false
            }
        };
        $httpBackend = _$httpBackend_;

        $httpBackend.when('GET', '/api/token/DASHBOARD').respond(200, {});
        $httpBackend.when('GET', '/api/settings').respond(200, {});
        $httpBackend.when('GET', '/controlbar/console/ip').respond(200, {});
        $httpBackend.when('GET', '/api/device').respond(200, {});
        $httpBackend.when('GET', '/api/dashboard/users').respond(200, {});

        $rootScope = _$rootScope_;
        $q = _$q_;
        $componentController = _$componentController_;
        ctrl = $componentController('dashboardWhitelist', {}, {});

        // making onInit call explicitly:
        // https://stackoverflow.com/questions/38631204/how-to-trigger-oninit-or-onchanges-implictly-in-unit-testing-angular-component
        ctrl.$onInit();

        // We have to make angular resolve the promise (WhitelistService), since it is not done
        // automatically because no template is involved:
        // https://stackoverflow.com/questions/24211312/angular-q-when-is-not-resolved-in-karma-unit-test
        $rootScope.$apply();
    }));

    describe('template tests', function() {
        let $compile, $ctrl, element, scope, mockSvg;

        /* jshint ignore:start */
        mockSvg = '<svg height="48" viewBox="0 0 24 24" width="48" xmlns="http://www.w3.org/2000/svg">\n' +
            '    <path d="M0 0h24v24H0z" fill="none"/>\n' +
            '    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>\n' +
            '</svg>';
        /* jshint ignore:end */

        beforeEach(inject(function(_$rootScope_, _$compile_) {
            scope = _$rootScope_.$new();

            $compile = _$compile_;

            $httpBackend.when('GET', '/api/token/DASHBOARD').respond(200, {});
            $httpBackend.when('GET', '/api/settings').respond(200, {});
            $httpBackend.when('GET', '/controlbar/console/ip').respond(200, {});
            $httpBackend.when('GET', '/api/device').respond(200, {});
            $httpBackend.when('GET', '/api/dashboard/users').respond(200, {});

            $httpBackend.when('GET', '/img/icons/ic_error.svg').respond(200, mockSvg);
            $httpBackend.when('GET', '/img/icons/ic_warning.svg').respond(200, mockSvg);
            $httpBackend.when('GET', '/img/icons/ic_help_outline.svg').respond(200, mockSvg);
            $httpBackend.when('GET', '/img/icons/ic_reorder.svg').respond(200, mockSvg);
            $httpBackend.when('GET', '/img/icons/ic_list_black.svg').respond(200, mockSvg);

            element = angular.element('<dashboard-whitelist></dashboard-whitelist>');
            element = _$compile_(element)(scope);
            scope.$apply();

            const isoScope = element.isolateScope();
            $ctrl = isoScope.$ctrl;

            // $httpBackend.flush();
        }));

        // it('should render the card', function() {
        //     var mdCardContent = element.find(utilsHtmlString);
        //     expect(angular.isDefined(mdCardContent['0'])).toBe(true);
        // });

        // describe('with details section opened', function() {
        //
        //     var elementWhitelistLabel,
        //         elementWhitelistTrackers,
        //         elementWhitelistAds;
        //
        //     beforeEach(function() {
        //         // ** get table data elements
        //         var mdCardContent = angular.element(element).find(utilsHtmlString);
        //         var divList = angular.element(mdCardContent).find('div');
        //         // console.log('divList: ', divList);
        //         // console.log('rowList: ', divList);
        //
        //         // index 0 is the table (see ng-if)
        //         var divTable = angular.element(divList[0]).find('div');
        //         var divTableBody = angular.element(divTable[5]).find('div');
        //         var divTableBodyRow = angular.element(divTableBody[0]).find('div');
        //
        //         // ** set elements first row in table (see mocklist)
        //         elementWhitelistLabel = angular.element(divTableBodyRow[0]).find('span'); // index 0 is 'empty-list'
        //         elementWhitelistTrackers = angular.element(divTableBodyRow[1]).find('md-checkbox');
        //         elementWhitelistAds = angular.element(divTableBodyRow[5]).find('md-checkbox');
        //     });
        //
        //     it('checkboxes should have the corret aria-labels', function() { // jshint ignore:line
        //         expect(elementWhitelistTrackers.attr('aria-label')).toMatch('Block trackers');
        //         expect(elementWhitelistAds.attr('aria-label')).toMatch('Block ads');
        //
        //     });
        //
        //     it('should toggle the whitelist setting on checkbox select/deselect', function() { // jshint ignore:line
        //         expect($ctrl.whitelist[0].ads).toBe(true);
        //         expect($ctrl.whitelist[0].trackers).toBe(false);
        //
        //         elementWhitelistAds.triggerHandler('click');
        //         elementWhitelistTrackers.triggerHandler('click');
        //
        //         expect($ctrl.whitelist[0].ads).toBe(false);
        //         expect($ctrl.whitelist[0].trackers).toBe(true);
        //     });
        //
        // jshint ignore:line
        //     it('should not have the not-whitelisted css class if whitelisting enabled', function() {
        //         expect(elementWhitelistLabel.hasClass('not-whitelisted')).toBe(false);
        //     });
        //
        // jshint ignore:line
        //     it('should set the not-whitelisted css class if whitelisting disabled', function() {
        //         elementWhitelistAds.triggerHandler('click');
        //         scope.$apply();
        //         expect(elementWhitelistLabel.hasClass('not-whitelisted')).toBe(true);
        //     });
        // });
    });

    describe('controller tests', function() {
        describe('getWhitelist', function() {
            it('should download the whitelist in correct format and sorted alphabetically', function() {
                expect(ctrl.whitelist.length).toEqual(3);
                expect(ctrl.whitelist[0].domain).toMatch('A eblocker.org');
                expect(ctrl.whitelist[0].ads).toBe(true);
                expect(ctrl.whitelist[0].trackers).toBe(false);
                expect(ctrl.whitelist[1].domain).toMatch('B eblocker.com');
                expect(ctrl.whitelist[1].ads).toBe(true);
                expect(ctrl.whitelist[1].trackers).toBe(true);
                expect(ctrl.whitelist[2].domain).toMatch('C eblocker.de');
                expect(ctrl.whitelist[2].ads).toBe(false);
                expect(ctrl.whitelist[2].trackers).toBe(true);
            });

            it('should keep deleted entries', function() { // jshint ignore:line
                ctrl.whitelist[1].trackers = false;
                ctrl.whitelist[1].ads = false;
                ctrl.updateWhitelistEntry(ctrl.whitelist[1]);
                mockWhitelist = {
                    'C eblocker.de': {
                        ads: false,
                        trackers: true
                    },
                    'A eblocker.org': {
                        ads: true,
                        trackers: false
                    }
                };
                ctrl.getWhitelist();
                $rootScope.$apply();
                // mockwhitelist has 2 entries, one has been deleted which is kept in list
                expect(ctrl.whitelist.length).toEqual(3);
            });

            it('should set trackers and ads of deleted entries to false', function() { // jshint ignore:line
                // This could happen if the entry is implicitly removed via the controlbar
                // the server will not send the entry anymore, but the local whitelist keeps
                // it as deleted, however the values have to be set to false.
                ctrl.whitelist[1].trackers = true;
                ctrl.whitelist[1].ads = false;
                ctrl.whitelist[1].marker = true;
                ctrl.updateWhitelistEntry(ctrl.whitelist[1]);
                mockWhitelist = {
                    'C eblocker.de': {
                        ads: false,
                        trackers: true
                    },
                    'A eblocker.org': {
                        ads: true,
                        trackers: false
                    }
                };
                ctrl.getWhitelist();
                $rootScope.$apply();
                expect(ctrl.whitelist.length).toEqual(3);
                // check that we have the right entry
                expect(ctrl.whitelist[1].marker).toBe(true);
                expect(ctrl.whitelist[1].trackers).toBe(false);
                expect(ctrl.whitelist[1].ads).toBe(false);
            });
        });

        describe('mergeLists', function() { // jshint ignore:line
            it('should add all deleted entries to the whitelist that are not in the whitelist', function () {
                var wl = [{domain: 'a test'}, {domain: 'c test'}, {domain: 'd test'}];
                var dl = [{domain: 'b test'}, {domain: 'd test'}];
                var res = ctrl.mergeLists(wl, dl);
                expect(res.length).toEqual(4);
            });
        });

        describe('updateWhitelistEntry', function() {
            it('should set the whitelist entry in correct format', function () {
                /* jshint ignore:start */
                spyOn(mockWhitelistService, 'updateWhitelistEntry').and.callThrough();
                var entry = ctrl.whitelist[0];
                ctrl.updateWhitelistEntry(entry);
                expect(mockWhitelistService.updateWhitelistEntry).toHaveBeenCalledWith(entry);
                /* jshint ignore:end */
            });
        });

        describe('isEntryWhitelisted', function() {
            it('should return true is either ads or trackers of entry are whitelisted', function () {
                var entryWhitelisted1 = {
                    domain: 'www.whitelist-one.de',
                    ads: true,
                    trackers: true
                };
                var entryWhitelisted2 = {
                    domain: 'www.whitelist-two.de',
                    ads: false,
                    trackers: true
                };
                var entryWhitelisted3 = {
                    domain: 'www.whitelist-three.de',
                    ads: true,
                    trackers: false
                };
                var entryNotWhitelisted = {
                    domain: 'www.not-whitelisted.de',
                    ads: false,
                    trackers: false
                };
                expect(ctrl.isEntryWhitelisted(entryWhitelisted1)).toBe(true);
                expect(ctrl.isEntryWhitelisted(entryWhitelisted2)).toBe(true);
                expect(ctrl.isEntryWhitelisted(entryWhitelisted3)).toBe(true);
                expect(ctrl.isEntryWhitelisted(entryNotWhitelisted)).toBe(false);
            });
        });
    });
});
