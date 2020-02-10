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
if (this === top) {

    var testCurrentParam = '@TEST_CURRENT@';
    var isTestCurrtent = testCurrentParam.toString() === 'true';

    var testRenewalParam = '@TEST_RENEWAL@';
    var isTestRenewal = testRenewalParam.toString() === 'true';

    if (isTestCurrtent) {
        testSsl('@URL_CURRENT_CA@', '@URL_REPORT_ERROR_CURRENT@');
    }
    if (isTestRenewal) {
        testSsl('@URL_RENEWAL_CA@', '@URL_REPORT_ERROR_RENEWAL@');
    }
}

function testSsl(url, errorUrl) {
    var testSslXhr = new XMLHttpRequest();
    testSslXhr.onreadystatechange = function () {
        if (testSslXhr['readyState'] === 4 && testSslXhr['status'] !== 200) {
            var reportFailure = new XMLHttpRequest();
            reportFailure.open('POST', errorUrl, true);
            reportFailure.send();
        }
    };
    testSslXhr.open('GET', url, true);
    testSslXhr.send();
}
