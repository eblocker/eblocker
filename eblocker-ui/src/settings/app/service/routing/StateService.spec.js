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
describe('App: settings; StateService', function() {
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    let stateService;

    beforeEach(angular.mock.module(function($provide, $translateProvider) {
        // Workaround angular-translate issue:
        // https://angular-translate.github.io/docs/#/guide/22_unit-testing-with-angular-translate
        $translateProvider.translations('en', {});
    }));

    beforeEach(inject(function(_StateService_) {
        stateService = _StateService_;
        stateService.setStates([{name: 'general', url: 'general'}]);
    }));

    describe('isStateValid', function() {
        it('should verify input against existing states and return false if invalid (\'test\')', function() {
            expect(stateService.isStateValid('test')).toBe(false);
        });
        // it('should verify input against existing states and return false if invalid (\'non-existent\')', function() {
        //     expect(stateService.isStateValid('non-existent')).toBe(false);
        // });
        it('should verify input against existing states and return true if valid (\'general\')', function() {
            expect(stateService.isStateValid('general')).toBe(true);
        });
        // it('should verify input against existing states and return true if valid (\'parentalcontrol\')', function() {
        //     expect(stateService.isStateValid('parentalcontrol')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'devices\')', function() {
        //     expect(stateService.isStateValid('devices')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'ssl\')', function() {
        //     expect(stateService.isStateValid('ssl')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'apps\')', function() {
        //     expect(stateService.isStateValid('apps')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'anonymization\')', function() {
        //     expect(stateService.isStateValid('anonymization')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'system\')', function() {
        //     expect(stateService.isStateValid('system')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'network\')', function() {
        //     expect(stateService.isStateValid('network')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'advanced\')', function() {
        //     expect(stateService.isStateValid('advanced')).toBe(true);
        // });
        // it('should verify input against existing states and return true if valid (\'tools\')', function() {
        //     expect(stateService.isStateValid('tools')).toBe(true);
        // });
    });

});
