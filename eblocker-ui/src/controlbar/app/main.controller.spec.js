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
describe('App controlbar; Main controller', function() { // jshint ignore: line
    beforeEach(angular.mock.module('template.controlbar.app'));
    beforeEach(angular.mock.module('eblocker.controlbar'));

    let ctrl, mockUserService, mockRegistrationService, mockUserProfile, mockMessages;

    let mockLocale = {
        language: 'en'
    };

    let mockWindowService = {
        innerWidth: 1300,
        addEventListener: function(type, handle) {
            // ignore this for mocking
        }
    };

    mockUserService = {
        getAssignedUser: function() {
            return {id: 2};
        },

        getOperatingUser: function() {
            return {id: 2};
        }
    };

    mockRegistrationService = {
        getRegistrationInfo: function() {
            return {
                isRegistered: true,
                licenseAboutToExpire: false
            };
        },
        hasProductKey: function() {
            return true;
        }
    };

    mockUserProfile = {
        hidden: false,
        standard: true,
        controlmodeTime: true
    };

    mockMessages = [{id: 1}];

    beforeEach(angular.mock.module(function($provide) {
        $provide.value('locale', mockLocale);
        $provide.value('$window', mockWindowService);
        $provide.value('UserService', mockUserService);
        $provide.value('RegistrationService', mockRegistrationService);
        $provide.value('userProfile', mockUserProfile);
        $provide.value('allMessages', mockMessages);
    }));

    beforeEach(inject(function($rootScope, $controller) {
        ctrl = $controller('MainController', {
        });
    }));

    describe('initially', function() {
        beforeEach(function() {
            mockWindowService.innerWidth = 300;
            // call onInit after innerWidth has been set to make sure onInit uses the right value.
            ctrl.$onInit();
        });

        it('should create a controller instance', function() {
            expect(angular.isDefined(ctrl)).toBe(true);
        });

        it('should create a page for each entry (mockWindowService.innerWidth = 300)', function() { // jshint ignore: line
            expect(ctrl.entries.length).toEqual(11);
            expect(ctrl.allPages.length).toEqual(11);
            expect(ctrl.currentPage).toEqual(0);
        });
    });

    describe('getCurrentPageNumber', function() {
        beforeEach(function() {
            mockWindowService.innerWidth = 300;
        });

        it('should return the page number for an entry', function() {
            ctrl.allPages.forEach((page, index) => {
                // in case of one entry per page, the current page for each entry is
                // the index of the page.
                expect(ctrl.getCurrentPageNumber(page[0], ctrl.allPages)).toEqual(index);
            });
        });
    });

    describe('getOverallWidth', function() {
        it('should add up a certain property', function() {
            const arr = [{num: 1}, {num: 2}, {num: 3}, {num: 6}];
            expect(ctrl.getOverallWidth(arr, 'num')).toEqual(12);
        });
    });

    describe('doesElementFit', function() {
        it('should return false if maxWidth of element exceeds available width', function() {
            const arr = [{maxWidth: 100}, {maxWidth: 50}, {maxWidth: 50}, {maxWidth: 80}];
            const el = {maxWidth: 30};
            expect(ctrl.doesElementFit(arr, el, 300)).toBe(false);
        });
        it('should return true if maxWidth of element is smaller than available width', function() {
            const arr = [{maxWidth: 100}, {maxWidth: 50}, {maxWidth: 50}, {maxWidth: 80}];
            const el = {maxWidth: 30};
            expect(ctrl.doesElementFit(arr, el, 500)).toBe(true);
        });
        it('should return false if maxWidth of element is eqaul to available width', function() {
            const arr = [{maxWidth: 100}, {maxWidth: 50}, {maxWidth: 50}, {maxWidth: 80}];
            const el = {maxWidth: 20};
            expect(ctrl.doesElementFit(arr, el, 300)).toBe(true);
        });
    });

    describe('getPages', function() {
        let array;
        beforeEach(function() {
            array = [
                {maxWidth: 100},
                {maxWidth: 100},
                {maxWidth: 100},
                {maxWidth: 100},
                {maxWidth: 100},
                {maxWidth: 100}
            ];
        });
        describe('getSinglePage', function() {
            it('should get page with all elements that fit', function () {
                const page = ctrl.getSinglePage(array, 1000);
                expect(page.length).toEqual(6);
            });
            it('should get page with one element if width is too small', function () {
                const page = ctrl.getSinglePage(array, 100);
                expect(page.length).toEqual(1);
            });
        });
        describe('getAllPages', function() {
            it('should get one page with six elements', function () {
                const pages = ctrl.getAllPages(array, 1000);
                expect(pages.length).toEqual(1); // one page
                expect(pages[0].length).toEqual(6); // six elements
            });

            it('should get six pages with one element each', function () {
                const pages = ctrl.getAllPages(array, 100);
                expect(pages.length).toEqual(6); // six pages
                expect(pages[0].length).toEqual(1); // one element each
                expect(pages[5].length).toEqual(1); // one element each
            });

            it('should get six pages with one element each', function () {
                const pages = ctrl.getAllPages(array, 600);
                // 600 should be enough (6 * 100), but we have a margin
                // of 250 pixels to account for paginator, eBlocker icon
                // and some margin for usability
                expect(pages.length).toEqual(2); // two pages
                expect(pages[0].length).toEqual(3); // three element each
                expect(pages[1].length).toEqual(3); // three element each
            });
        });
    });
});
