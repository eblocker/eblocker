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

    IdleProvider.autoResume(true);

    IdleProvider.interrupt(IDLE_TIMES.EVENTS);
    IdleProvider.idle(IDLE_TIMES.IDLE); // Seconds the user may be idle before dialog opens
    IdleProvider.timeout(IDLE_TIMES.TIMEOUT); // Seconds counting down until the session expires

    KeepaliveProvider.interval(IDLE_TIMES.KEEP_ALIVE); // The keepalive ping will be sent every 300 seconds.

    TitleProvider.enabled(false); // breaks in Safari when Javascript is paused
}
