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

function get(request, response, next) {
    console.log('security.get...');
    setTimeout(function() {
        console.log('...security.get');
        response
            .status(200)
            .send({
                'token': 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJo' +
                'dHRwczovLzE5Mi4xNjguMy4xNzY6MzQ0MyIsImF1ZC' +
                'I6Imh0dHBzOi8vMTkyLjE2OC4zLjE3NjozNDQzIiwiZXhwIjoxNDkxMjA5' +
                'MTQ0LCJpYXQiOjE0OTEyMDU1NDQsImFjeCI6IkRBU0' +
                'hCT0FSRCJ9.DHR2NaCLmUDhFe8NnLacHWUS0B9yrJd9GS_XHvlHxms',
                'appContext': request.params.appContext,
                'expiresOn': (new Date()).getTime() / 1000 + 3600,
                'passwordRequired': false
            })
            .end();
    }, 1000);
}
