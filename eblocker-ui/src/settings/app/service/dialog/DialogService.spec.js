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
/* global jasmine */
import 'angular-mocks';
import dialogServiceFactory from './DialogService';

describe('App settings; DialogService confirmation contract', function() {
    let $q;
    let dialogService;
    let mdDialog;

    beforeEach(angular.mock.module('template.settings.app'));
    beforeEach(angular.mock.module('eblocker.adminconsole'));

    beforeEach(inject(function(_$q_) {
        $q = _$q_;

        mdDialog = {
            show: jasmine.createSpy('show')
                .and.returnValue($q.when('closed'))
        };

        dialogService = dialogServiceFactory(
            mdDialog,
            $q,
            {instant: angular.noop}
        );
    }));

    it('exposes a generic confirmation dialog for settings actions',
        function() {
            const subject = {id: 8};
            const okAction = jasmine.createSpy('okAction')
                .and.returnValue($q.when(true));
            const cancelAction = jasmine.createSpy('cancelAction');

            dialogService.confirmationDialog(
                {},
                'TITLE',
                'TEXT',
                'OK',
                'CANCEL',
                subject,
                okAction,
                cancelAction
            );

            expect(mdDialog.show).toHaveBeenCalled();

            const options =
                mdDialog.show.calls.mostRecent().args[0];

            expect(options.controller)
                .toBe('ConfirmationDialogController');
            expect(options.locals.msgKeys.title)
                .toBe('TITLE');
            expect(options.locals.msgKeys.text)
                .toBe('TEXT');
            expect(options.locals.msgKeys.okButton)
                .toBe('OK');
            expect(options.locals.msgKeys.cancelButton)
                .toBe('CANCEL');
            expect(options.locals.subject)
                .toBe(subject);
            expect(options.locals.okAction)
                .toBe(okAction);
            expect(options.locals.cancelAction)
                .toBe(cancelAction);
        });
});
