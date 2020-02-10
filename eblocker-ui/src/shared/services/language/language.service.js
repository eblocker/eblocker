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
export default function LanguageService($translate, moment) {
    'ngInject';

    function getDate(timestamp, format) {
        return moment(timestamp).locale($translate.use()).format($translate.instant(format));
    }

    function getDateFromString(dateString, format) {
        const f = $translate.instant(format);
        return moment(dateString, f);
    }

    function getCurrentDate(format) {
        return moment().locale($translate.use()).format($translate.instant(format));
    }

    function calculateAge(time) {
        const todayDate = new Date();
        const birthDate = new Date(time);
        const todayMonth = todayDate.getMonth();
        const todayDay = todayDate.getDate();
        const birthMonth = birthDate.getMonth();
        const birthDay = birthDate.getDate();

        let age = todayDate.getFullYear() - birthDate.getFullYear();

        if ((todayMonth < birthMonth) ||
            ((birthMonth === todayMonth) && (todayDay < birthDay))) {
            // not birthday month yet || it's birthday month, but not yet birthday
            age--;
        }
        return age;
    }

    function convertTimeToStringByLanguage(hours, minutes, lang) {
        if (lang === 'en') {
            // onlineTime.getAsAmPm(time);
            let ret = '';
            if (hours < 12) {
                ret = getPaddedHours(hours) + ':' + minutes + ' am';
            } else if (hours > 12 && hours < 24) {
                ret = getPaddedHours(hours - 12) + ':' + minutes + ' pm';
            } else {
                ret = '12:' + minutes + (hours === 12 ? ' pm' : ' am'); //12 = 12pm  / 24 = 12am
            }
            return ret;
        } else { // de
            return getPaddedHours(hours) + ':' + minutes + ' Uhr';
        }
    }

    function getPaddedHours(hours) {
        return (hours < 10 ? '0' : '') + hours;
    }

    return {
        getDate: getDate,
        getCurrentDate: getCurrentDate,
        getDateFromString: getDateFromString,
        calculateAge: calculateAge,
        convertTimeToStringByLanguage: convertTimeToStringByLanguage
    };
}
