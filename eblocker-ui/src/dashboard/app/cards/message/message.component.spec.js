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
describe('Component: dashboardMessage', function() {
    beforeEach(angular.mock.module('eblocker.dashboard'));
    // ** Not Sure why these modules have to be included here, since they are
    // already included in 'eblocker.dashboard'.
    // beforeEach(angular.mock.module('eblocker.logger'));
    // beforeEach(angular.mock.module('eblocker.security'));

    var $componentController, ctrl;

    const mockDataService = {
        registerComponentAsServiceListener: function() {},
        unregisterComponentAsServiceListener: function() {}
    };

    const mockCardService = {
    };

    beforeEach(angular.mock.module(function($provide) {
        $provide.value('CardService', mockCardService);
        $provide.value('DataService', mockDataService);
    }));

    beforeEach(inject(function(_$componentController_) {
        $componentController = _$componentController_;
        ctrl = $componentController('dashboardMessage', {}, {});
    }));

    describe('dashboardMessage', function() {
        it('should set loading status on refreshMessages', function() {
            ctrl.refreshMessages();
            expect(ctrl.isMessageLoading()).toBe(true);
        });
    });

});
