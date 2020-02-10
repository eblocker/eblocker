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
export default function AccessContingentService($translate) {
    'ngInject';

    const contingentDays = {
        1: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_1',
        2: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_2',
        3: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_3',
        4: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_4',
        5: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_5',
        6: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_6',
        7: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_7',
        8: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_8',
        9: 'SHARED.PARENTAL_CONTROL.ACCESS_CONTINGENTS.PARENTAL_CONTROL_DAY_9'
    };

    function getContingentDay(contingent) {
        return contingentDays[contingent.onDay];
    }

    function getContingentDisplayTime(minutesFromMidnight) {
        let hours = Math.floor(minutesFromMidnight / 60);
        let minutes = minutesFromMidnight % 60;
        let ampm = '';
        let zeroMinutes = (minutes < 10 ? '0' : '');

        if ($translate.use() === 'en') {
            if (hours <= 11) {
                ampm = ' am';
            } else if (hours === 12) {
                ampm = ' pm';
            } else if (hours <= 23) {
                hours -= 12;
                ampm = ' pm';
            } else {
                hours -= 12;
                ampm = ' am';
            }
        }
        return hours + ':' + zeroMinutes + minutes + ampm;
    }

    return {
        getContingentDay: getContingentDay,
        getContingentDisplayTime: getContingentDisplayTime
    };
}
