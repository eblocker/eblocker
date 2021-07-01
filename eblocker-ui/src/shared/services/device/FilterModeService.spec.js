/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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

describe('FilterModeService', function() {
    beforeEach(angular.mock.module('eblocker.dashboard'));

    let service, FILTER_TYPE;

    beforeEach(inject(function(_FilterModeService_, _FILTER_TYPE_) {
        service = _FilterModeService_;
        FILTER_TYPE = _FILTER_TYPE_;
    }));

    it('recognizes domain filter', function() {
        checkEffectiveFilterType('PLUG_AND_PLAY', false, true, FILTER_TYPE.DNS);
    });

    it('recognizes explicit pattern filter', function() {
        checkEffectiveFilterType('ADVANCED', true, true, FILTER_TYPE.PATTERN);
        checkEffectiveFilterType('ADVANCED', false, true, FILTER_TYPE.PATTERN);
    });

    it('recognizes no filter', function() {
        checkEffectiveFilterType('NONE', true, true, FILTER_TYPE.NONE);
    });

    it('selects pattern filter automatically if SSL is enabled', function() {
        checkEffectiveFilterType('AUTOMATIC', true, true, FILTER_TYPE.PATTERN);
    });

    it('selects domain filter automatically if SSL is disabled', function() {
        checkEffectiveFilterType('AUTOMATIC', false, true, FILTER_TYPE.DNS);
        checkEffectiveFilterType('AUTOMATIC', true, false, FILTER_TYPE.DNS);
    });

    function checkEffectiveFilterType(deviceFilterMode, deviceSslEnabled, globalSslEnabled, expectedFilterType) {
        const device = {
            filterMode: deviceFilterMode,
            sslEnabled: deviceSslEnabled
        };
        expect(service.getEffectiveFilterMode(globalSslEnabled, device)).toEqual(expectedFilterType);
    }
});
