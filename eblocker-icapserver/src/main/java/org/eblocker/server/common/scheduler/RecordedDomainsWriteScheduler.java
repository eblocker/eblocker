/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.scheduler;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.recorder.DomainRequestRecorder;

/**
 * Ensures that recorded domains are saved to Redis regularly (and on shutdown).
 */
public class RecordedDomainsWriteScheduler extends FixedRateScheduler {
    private final DomainRequestRecorder recorder;

    @Inject
    public RecordedDomainsWriteScheduler(@Named("executor.recorded.domains.writer.startupDelay") long initialDelayInSeconds,
                                         @Named("executor.recorded.domains.writer.fixedDelay") long periodInSeconds,
                                         DomainRequestRecorder recorder) {
        super(recorder::saveCurrent, initialDelayInSeconds, periodInSeconds);
        this.recorder = recorder;
    }

    public void shutdown() {
        recorder.saveCurrent();
    }
}
