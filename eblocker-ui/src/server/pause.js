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
    get: get,
    put: put
};

var pauseUntil = 0;
var pausingAllowed = true;

function now() {
    return Math.floor(Date.now() / 1000);
}

function getPausing() {
    return Math.max(pauseUntil - now(), 0);
}

function get(request, response, next) {
    var data = {
        pausing: getPausing(),
        pausingAllowed: pausingAllowed
    };

    response
        .status(200)
        .send(data)
        .end();
}

function put(request, response, next) {
    var pausing = request.body.pausing;

    if (pausing === 0) {
        pauseUntil = 0;
    } else {
        pauseUntil = now() + pausing;
    }

    var data = {
        pausing: getPausing(),
        pausingAllowed: pausingAllowed
    };

    response
        .status(200)
        .send(data)
        .end();
}
