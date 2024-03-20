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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Singleton
public class ShutdownService {

    private static final Logger LOG = LoggerFactory.getLogger(ShutdownService.class);

    private final SystemStatusService systemStatusService;
    private final ScriptRunner scriptRunner;
    private final String shutdownScript;
    private final String rebootScript;
    private final ShutdownExecutorService shutdownExecutorService;

    private EventLogger eventLogger;
    private DataSource dataSource;

    @Inject
    public ShutdownService(
            SystemStatusService systemStatusService,
            ScriptRunner scriptRunner,
            @Named("shutdown.command") String shutdownScript,
            @Named("reboot.command") String rebootScript,
            ShutdownExecutorService shutdownExecutorService
    ) {
        this.systemStatusService = systemStatusService;
        this.scriptRunner = scriptRunner;
        this.rebootScript = rebootScript;
        this.shutdownScript = shutdownScript;
        this.shutdownExecutorService = shutdownExecutorService;
    }

    /**
     * Cannot be inject in constructore, because eventLogger might not yet be available at that time!
     * So this service cannot rely on having a valid eventLogger at all times!
     */
    public void setEventLogger(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    /**
     * Cannot be inject in constructore, because dataSource might not yet be available at that time!
     * So this service cannot rely on having a valid dataSource at all times!
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void shutdown() {
        ensureNotUpdating();
        LOG.info("eBlocker will SHUTDOWN now!");
        shutdownOrReboot(false);
        systemStatusService.setExecutionState(ExecutionState.SHUTTING_DOWN);
    }

    public void reboot() {
        ensureNotUpdating();
        LOG.info("eBlocker will REBOOT now!");
        shutdownOrReboot(true);
        systemStatusService.setExecutionState(ExecutionState.SHUTTING_DOWN_FOR_REBOOT);
    }

    private void ensureNotUpdating() {

        if (systemStatusService.getExecutionState() == ExecutionState.UPDATING) {
            String msg = "Cannot reboot/shut down eBlocker while it is updating.";
            LOG.error(msg);
            throw new EblockerException(msg);
        }
    }

    /**
     *
     */
    private void shutdownOrReboot(boolean reboot) {
        String script = reboot ? rebootScript : shutdownScript;
        if (eventLogger != null) {
            eventLogger.log(Events.systemEvent(reboot));
        }
        if (dataSource != null) {
            //Save Redis database to disk
            dataSource.createSnapshot();
        }
        shutdownExecutorService.shutdownExecutorServices();

        //
        // Use extra thread for the actual shutdown command, so that the caller
        // of this method gets an immediate, valid response.
        //
        new Thread(() -> {
            try {
                Thread.sleep(10000L);//sleep for a while so that the frontend gets HTTP response
                scriptRunner.runScript(script);

            } catch (IOException e) {
                String msg = "Cannot execute " + (reboot ? "reboot" : "shutdown") + " command " + script;
                LOG.error(msg, e);
                throw new EblockerException(msg, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String msg = "Cannot execute " + (reboot ? "reboot" : "shutdown") + " command " + script;
                LOG.error(msg, e);
                throw new EblockerException(msg, e);
            }
        }).start();
    }

}
