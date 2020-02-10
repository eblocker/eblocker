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
export default function(IdleProvider, KeepaliveProvider, TitleProvider, IDLE_TIMES) {
    'ngInject';

    IdleProvider.interrupt(IDLE_TIMES.EVENTS);
    IdleProvider.autoResume(true);
    // 1200/2 = 600 seconds (doubled by onBlurHandler in main.component.js, so all in all we end up with 1200s again)
    IdleProvider.idle(IDLE_TIMES.IDLE / 2);
    IdleProvider.timeout(0); // 0 seconds (no dialog is shown, so timeout immediately)

    KeepaliveProvider.interval(IDLE_TIMES.KEEP_ALIVE); // 300 seconds

    // disable timeout in title (not needed here - no dialog. And breaks in Safari when javascript is paused)
    TitleProvider.enabled(false);
}
