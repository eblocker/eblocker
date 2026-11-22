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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eblocker.server.common.system;

import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class DefaultFatalErrorHandler implements FatalErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultFatalErrorHandler.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Override
    public void onFatalError(String source, Throwable error) {
        if (shuttingDown.compareAndSet(false, true)) {
            LOG.error("Fatal JVM/subsystem error in {}; terminating process so systemd can restart icapserver", source, error);
            haltProcess(1);
        } else {
            LOG.error("Additional fatal error in {} while process termination is already in progress", source, error);
        }
    }

    protected void haltProcess(int status) {
        Runtime.getRuntime().halt(status);
    }
}
