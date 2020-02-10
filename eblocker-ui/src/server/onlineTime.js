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
    start: start,
    stop: stop
};

var totalTime = 3600 * 2;
var online = false;
var remainingTime = 3600 * 1.5;
var speed = 20;
var timer;

function start(request, response, next) {
    online = true;
    timer = setInterval(function() {
        console.log('remainingTime: ' + remainingTime);
        remainingTime -= speed;
    }, 1000);
    response
        .status(200)
        .send(true)
        .end();
}

function stop(request, response, next) {
    online = false;
    clearInterval(timer);
    timer = undefined;
    response
        .status(200)
        .send()
        .end();
}

function get(request, response, next) {
    var data = {
        active: online,
        allowed: true,
        usedTime: {seconds: totalTime - remainingTime},
        accountedTime: {seconds: totalTime - remainingTime},
        maxUsageTime: {seconds: totalTime}
    };

    response
        .status(200)
        .send(data)
        .end();
}
