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
package org.eblocker.server.common.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.strategicgains.syntaxe.ValidationEngine;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.service.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is used to automatically run the system updates every day in a
 * given time frame at a random time, so that the chance that all eBlocker
 * devices try to request the server for updates at the same time is low.
 * Therefore should the server load stay small, because it does not have to
 * handle lots of parallel update requests.
 */
@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class AutomaticUpdater {
    private static final Logger log = LoggerFactory.getLogger(AutomaticUpdater.class);

    private final ObjectMapper objectMapper;
    private final SystemUpdater updater;
    private final ScheduledExecutorService executorService;
    private final DataSource datasource;
    private final String configJSON;
    private ScheduledFuture<?> taskFuture;

    private LocalDateTime nextUpdate;
    private AutomaticUpdaterConfiguration configuration;
    private boolean isActivated = true;
    private long delayToNextUpdate;
    private Clock clock;
    private Random random;

    private RegistrationService registrationService;

    @Inject
    public AutomaticUpdater(
        @Named("lowPrioScheduledExecutor") ScheduledExecutorService executorService,
        SystemUpdater updater,
        DataSource datasource,
        ObjectMapper objectMapper,
        @Named("update.automatic.config") String configJSON,
        @Named("localClock") Clock clock,
        Random random,
        RegistrationService registrationService
    ) {
        this.executorService = executorService;
        this.updater = updater;
        this.datasource = datasource;
        this.objectMapper = objectMapper;
        this.configJSON = configJSON;
        this.clock = clock;

        this.random = random;
        this.registrationService = registrationService;
    }

    @SubSystemInit
    public void init() {
        //check if AutomaticUpdater was active before reboot
        getLastAutomaticUpdaterState(datasource);

        if (!loadConfigFromDatasource(datasource)) {//Try loading the configuration from Redis, if not possible load with injected default data from configuration.properties
            try {
                this.configuration = objectMapper.readValue(configJSON, AutomaticUpdaterConfiguration.class);
                log.info("Default AutoUpdate config was loaded from configuration.properties");
            } catch (IOException e) {
                log.error("Error on init subsystem", e);
            }
        } else {
            log.info("AutoUpdate config was loaded from Redis.");
        }
    }

    /**
     * Check if the AutomaticUpdater was active before restarting the ICAP server
     *
     * @param datasource
     */
    private void getLastAutomaticUpdaterState(DataSource datasource) {
        String updaterState = datasource.getAutomaticUpdatesActivated();
        if (updaterState != null && updaterState.equals("false")) { //by default updateState is true, just change if it was false before rebooting ICAP-server
            isActivated = false;
        }
    }


    /**
     * Loads the timeframe for automatic updates from the redis datasource
     */
    private boolean loadConfigFromDatasource(DataSource datasource) {
        AutomaticUpdaterConfiguration config = datasource.getAutomaticUpdateConfig();
        if (config != null) {
            List<String> errors = ValidationEngine.validate(config);
            if (errors.isEmpty()) {
                this.configuration = config;
                return true;
            }
        }
        return false;
    }


    /**
     * Start the update process; change this logic to control when updates are performed
     */
    public void start() {
        stopUpdateTask();
        delayToNextUpdate = calculateNextUpdateAndDelay();
        startUpdatingTask(false); //start update later in time frame today
    }

    /**
     * Update the time frame for automatic updates
     */
    public void setNewConfiguration(AutomaticUpdaterConfiguration config) {
        log.info("user entered new autoupdate config: {} -> {}", config.getNotBefore(), config.getNotAfter());

        //update and save the users configuration
        configuration = config;
        datasource.save(configuration);

        start();
    }

    /**
     * Stop the last scheduled update, if there is one
     */
    private void stopUpdateTask() {
        //stop next update tasks (if it is not currently running)
        if (taskFuture != null)
            taskFuture.cancel(false);
    }

    private void startUpdatingTask(boolean now) {
        if (updater != null && isActivated) {
            Runnable updateTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        registrationService.checkAndExecuteUpgrade();
                    } catch (Exception e) {
                        log.error("Cannot check for license upgrade", e);
                    }

                    try {
                        log.info("Starting system updater now.");

                        updater.startUpdate();

                        //next Update tomorrow
                        prepareUpdateTomorrow();

                        // schedule next Update
                        startUpdatingTask(false);
                    } catch (EblockerException | IOException e) {
                        log.error("Cannot check for system update", e);
                    } catch (InterruptedException e) {
                        log.error("Update task interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
            };
            if (now) {//start update now
                executorService.execute(updateTask);
            } else {
                taskFuture = executorService.schedule(updateTask, delayToNextUpdate, TimeUnit.MINUTES);
            }
        }
    }

    /**
     * Calculate and update time for tomorrow
     */
    private void prepareUpdateTomorrow() {
        delayToNextUpdate = calculateNextUpdateAndDelay();// create new random time for tomorrow (in given time frame)
    }

    /**
     * Calculate a random delay for the updates (from now) between
     * startTimeHour:startTimeMin (e.g. 23:50) and endTimeHour:endTimeMin (e.g.
     * 02:00) in minutes
     *
     * @return delay for update execution in minutes
     */
    private long calculateNextUpdateAndDelay() {
        LocalDateTime now = LocalDateTime.now(clock);
        nextUpdate = calculateNextUpdate(now, configuration.getNotBefore(), configuration.getNotAfter(), random.nextDouble());
        log.info("The next automatic update will be started around : "
            + nextUpdate.format(DateTimeFormatter.ofPattern("HH:mm 'on the' dd.MM.yyyy")));
        return LocalDateTime.now(clock).until(nextUpdate, ChronoUnit.MINUTES) + 1;
        // +1 because .until() returns only rounded amounts of minutes
    }

    /**
     * Calculate date and time for the next update.
     * If now is before the time window, the next update will be within the time window.
     * If now is within or after the time window, the next update will be within the time window on the next day.
     *
     * @param now        the current date and time
     * @param notBefore  start time of the window
     * @param notAfter   end time of the window
     * @param random0to1 a random number between 0.0 and 1.0
     * @return date and time of the next update
     */
    public static LocalDateTime calculateNextUpdate(LocalDateTime now, LocalTime notBefore, LocalTime notAfter, double random0to1) {
        LocalDateTime t0 = now.with(notBefore);
        LocalDateTime t1 = now.with(notAfter);
        if (t1.isBefore(t0)) { // e.g. when updates are scheduled between 23:00 and 1:00
            t1 = t1.plusDays(1);
        }
        if (now.isAfter(t0)) { // if we are now IN the time window or AFTER it, schedule for next day
            t0 = t0.plusDays(1);
            t1 = t1.plusDays(1);
        }
        long minutes = t0.until(t1, ChronoUnit.MINUTES);
        return t0.plusMinutes((long) (minutes * random0to1));
    }

    public LocalDateTime getNextUpdate() {
        return nextUpdate;
    }

    public void setActivated(boolean activated) {
        this.isActivated = activated;
        datasource.setAutomaticUpdatesActivated(activated);
        if (activated) {
            log.info("Automatic updates are enabled.");
            start();
        } else {
            log.info("Automatic updates are disabled.");
            stopUpdateTask();
            nextUpdate = null;
        }

    }

    public boolean isActivated() {
        return isActivated;
    }

    public AutomaticUpdaterConfiguration getConfiguration() {
        return configuration;
    }

}
