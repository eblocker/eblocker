/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.common.squid;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The SquidReloadingService is responsible for reloading the Squid service
 * after a configuration change asynchronously. It makes sure that the reloading is
 * not done too rapidly.
 */
@Singleton
public class SquidReloadingService {
    private static final Logger log = LoggerFactory.getLogger(SquidReloadingService.class);
    private final ScriptRunner scriptRunner;
    private final String squidReconfigureScript;
    private final ScheduledExecutorService executorService;
    private final Clock clock;
    private final int graceTimeBeforeReloads;
    private final int minimumTimeBetweenReloads;
    private long lastReload = 0;
    private ScheduledFuture reloadFuture;

    @Inject
    public SquidReloadingService(
            ScriptRunner scriptRunner,
            @Named("squidReconfigure.command") String squidReconfigureScript,
            @Named("highPrioScheduledExecutor") ScheduledExecutorService executorService,
            Clock clock,
            @Named("squid.config.graceTimeBeforeReload") Integer graceTimeBeforeReloads,
            @Named("squid.config.minimumTimeBetweenReloads") Integer minimumTimeBetweenReloads
            ) {
        this.scriptRunner = scriptRunner;
        this.squidReconfigureScript = squidReconfigureScript;
        this.executorService = executorService;
        this.clock = clock;
        this.graceTimeBeforeReloads = graceTimeBeforeReloads;
        this.minimumTimeBetweenReloads = minimumTimeBetweenReloads;
    }

    /**
     * Tell squid that its configuration has been updated, so please reconfigure
     */
    public synchronized void tellSquidToReloadConfig() {
        log.debug("squid reload requested at {} - last reload {} future done {} future delay {}", clock.millis(), lastReload, reloadFuture != null ? reloadFuture.isDone() : "-", reloadFuture != null ? reloadFuture.getDelay(TimeUnit.MILLISECONDS) : "-");

        long delay;
        if (reloadFuture == null) {
            delay = graceTimeBeforeReloads;
        } else if (reloadFuture.isDone()) {
            delay = Math.max(lastReload + minimumTimeBetweenReloads - clock.millis(), graceTimeBeforeReloads);
        } else if (reloadFuture.getDelay(TimeUnit.MILLISECONDS) <= 0) {
            delay = minimumTimeBetweenReloads;
        } else {
            log.info("ignoring reload request as one is already scheduled in {}ms.", reloadFuture.getDelay(TimeUnit.MILLISECONDS));
            return;
        }
        log.info("scheduling squid reload in {}ms", delay);
        reloadFuture = executorService.schedule(this::reloadSquid, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void reloadSquid() {
        log.info("reloading squid");
        try {
            synchronized (SquidReloadingService.this) {
                scriptRunner.runScript(squidReconfigureScript);
                lastReload = clock.millis();
            }
        } catch (Exception e) {
            log.error("Problem while running the squid reload script", e);
        }
    }
}
