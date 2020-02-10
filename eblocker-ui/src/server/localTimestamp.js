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
    'get': get
};

// {"instant":{"nano":312000000,"epochSecond":1498820650},"day":30,"dayOfWeek":5,"month":6,"year":2017,"hour":13,"minute":4,"second":10}

function get(request, response, next) {
    var now = new Date();

    console.log('localTimestamp.get...');
        response
            .status(200)
            .send({
                'instant': {
                    'nano': now.getMilliseconds()*1000,
                    'epochSeconds': now.getTime()
                },
                'day': now.getDate(),
                'dayOfWeek': now.getDay(),
                'month': now.getMonth(),
                'year': now.getFullYear(),
                'hour': now.getHours(),
                'minute': now.getMinutes(),
                'second': now.getSeconds()
            })
            .end();
}
