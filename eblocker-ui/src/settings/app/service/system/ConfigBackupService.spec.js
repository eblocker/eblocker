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
describe('ConfigBackup service', function() { // jshint ignore: line
    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    var $httpBackend, service;

    beforeEach(inject(function (_$httpBackend_, _ConfigBackupService_) {
        $httpBackend = _$httpBackend_;
        service = _ConfigBackupService_;
    }));

    afterEach(function() {
        $httpBackend.verifyNoOutstandingExpectation();
        $httpBackend.verifyNoOutstandingRequest();
    });

    /* disabled due to issue EB1-1662
    it('should upload with PUT', function() {
        $httpBackend.expectPUT('/api/configbackup/import', 'config file content').respond(200);
        service.importConfig('config file content');
        $httpBackend.flush();
    });
    */
});
