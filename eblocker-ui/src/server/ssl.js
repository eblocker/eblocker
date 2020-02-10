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
    post: post,
    getCertFile: getCertFile
};

var future = 112320000;
var futureInDays = future / 100 / 60 / 60 / 24;

var fs = require('fs');
var path = require('path');

var status = {
    globalSslStatus: true,
    deviceSslStatus: true,
    currentCertificateAvailable: true,
    currentCertificateInstalled: false,
    currentCertificate: {
        distinguishedName: { commonName: 'eBlocker Mocked Certificate'}
    },
    // currentCertificateEndDate: new Date(Date.now() +  112320000), // now + 13 days
    currentCertificateEndDate: {
        year: new Date(Date.now() +  future).year,
        month: new Date(Date.now() +  future).month,
        day: new Date(Date.now() +  future).day,
        daysTill: futureInDays
    },
    renewalCertificate: {
        distinguishedName: { commonName: 'eBlocker Mocked Renewed Certificate'}
    },
    renewalCertificateAvailable: true,
    renewalCertificateInstalled: false,
    renewalCertificateEndDate: new Date(Date.now() +  8640000000), // now + 100 days
};

function get(request, response, next) {
    response
        .status(200)
        .send(status)
        .end();
}

function post(request, response, next) {
    console.log('status.post received:', (request.body.newStatus ? 'enable' : 'disable'), 'SSL.');

    var responseStatus = request.body.newStatus;

    status.deviceSslStatus = responseStatus;

    response
        .status(200)
        .send(responseStatus)
        .end();
}

function getCertFile(request, response, next) {
    var relPath = path.join(__dirname, 'data/cert.pem');
    fs.readFile(relPath, function(err, data) {
        response
            .status(200)
            .header({
                'x-filename': 'mock-download.js',
                'content-type': 'application/x-x509-ca-cert'
            })
            .send(data)
            .end();
    });
}
