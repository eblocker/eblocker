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
var router = require('express').Router();
var four0four = require('./utils/404')();
var redirect = require('./redirect');
var settings = require('./settings');
var security = require('./security');
var pause    = require('./pause');
var onlineTime = require('./onlineTime');
var userProfile = require('./userProfile');
var localTimestamp = require('./localTimestamp');
var device = require('./device');
var ssl = require('./ssl');

router.put('/redirect/:decision/:txid', redirect.setDecision);
router.get('/settings', settings.get);
router.get('/token/:appContext', security.get);
router.get('/device/pause', pause.get);
router.put('/device/pause', pause.put);
router.get('/device', device.get);
router.get('/parentalcontrol/usage', onlineTime.get);
router.post('/parentalcontrol/usage', onlineTime.start);
router.delete('/parentalcontrol/usage', onlineTime.stop);
router.get('/userProfile', userProfile.get);
router.get('/localtimestamp', localTimestamp.get);
router.get('/ssl/status', ssl.get);
router.post('/ssl/device/status', ssl.post);
router.get('/ssl/caCertificate.crt', ssl.getCertFile);
router.get('/ssl/renewalCertificate.crt', ssl.getCertFile);
router.get('/*', four0four.notFoundMiddleware);

module.exports = router;
