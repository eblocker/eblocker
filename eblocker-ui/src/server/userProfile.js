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
module.exports = {
    get: get
};

var userProfile = {};

function get(request, response, next) {
    var data = {
        id: 1,
        name: 'bob',
        description: '',
        standard: false,
        hidden: false,
        accessibleSitesPackages: [],
        inaccessibleSitesPackages: [],
        internetAccessRestrictionMode: 1,
        internetAccessContingents: [
            {
                onDay: 3,
                fromMinutes: 0,
                tillMinutes: 60
            },
            {
                onDay: 3,
                fromMinutes: 18*60,
                tillMinutes: 19*60
            },
           {
                onDay: 1,
                fromMinutes: 240,
                tillMinutes: 360
            },
            {
                onDay: 1,
                fromMinutes: 480,
                tillMinutes: 960
            },
            {
                onDay: 5,
                fromMinutes: 0,
                tillMinutes: 1440
            }
        ],
        maxUsageTimeByDay: {
            MONDAY: 60,
            TUESDAY: 60,
            WEDNESDAY: 60,
            THURSDAY: 60,
            FRIDAY: 60,
            SATURDAY: 60,
            SUNDAY: 60
        },
        builtin: false,
        controlmodeUrls: false,
        controlmodeTime: true,
        controlmodeMaxUsage: true
    };

    response
        .status(200)
        .send(data)
        .end();
}
