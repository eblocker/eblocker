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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.strategicgains.syntaxe.ValidationEngine;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.UpdatingStatus;
import org.eblocker.server.common.update.AutomaticUpdater;
import org.eblocker.server.common.update.AutomaticUpdaterConfiguration;
import org.eblocker.server.common.update.SystemUpdater;
import org.eblocker.server.common.update.SystemUpdater.State;
import org.eblocker.server.http.controller.UpdateController;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.RegistrationService;
import org.eblocker.server.http.service.SystemStatusService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class UpdateControllerImpl implements UpdateController {
    private static final Logger LOG = LoggerFactory.getLogger(UpdateControllerImpl.class);

    private final SystemUpdater systemUpdater;
    private final AutomaticUpdater automaticUpdater;
    private final RegistrationService registrationService;
    private final SystemStatusService systemStatusService;
    private final ProductInfoService productInfoService;
    private final int secondsBetweenUpdateRequests;
    private Instant lastUpdateRequest = null;

    @Inject
    public UpdateControllerImpl(
            SystemUpdater systemUpdater,
            AutomaticUpdater autoUpdater,
            RegistrationService registrationService,
            SystemStatusService systemStatusService,
            ProductInfoService productInfoService,
            @Named("update.seconds.between.requests") int secondsBetweenUpdateRequests
    ) {
        this.systemUpdater = systemUpdater;
        this.automaticUpdater = autoUpdater;
        this.registrationService = registrationService;
        this.systemStatusService = systemStatusService;
        this.productInfoService = productInfoService;
        this.secondsBetweenUpdateRequests = secondsBetweenUpdateRequests;
    }

    @Override
    public UpdatingStatus getUpdatingStatus(Request request, Response response) throws IOException, InterruptedException {
        UpdatingStatus status = systemUpdater.getUpdatingStatus();
        status.activateAutomaticUpdates(automaticUpdater.isActivated());
        status.setNextAutomaticUpdate(formatDate(automaticUpdater.getNextUpdate()));
        status.setAutomaticUpdatesAllowed(productInfoService.hasFeature(ProductFeature.AUP));
        systemStatusService.setUpdatingStatus(status);
        status.setConfig(automaticUpdater.getConfiguration());
        return status;
    }

    @Override
    public UpdatingStatus getAutoUpdateInformation(Request request, Response response) throws IOException, InterruptedException {
        UpdatingStatus status = getUpdatingStatus(request, response);
        status.setConfig(automaticUpdater.getConfiguration());
        return status;
    }

    @Override
    public UpdatingStatus setAutomaticUpdatesStatus(Request request, Response response) throws IOException, InterruptedException {
        UpdatingStatus status = request.getBodyAs(UpdatingStatus.class);
        automaticUpdater.setActivated(status.isAutomaticUpdatesActivated());
        return getAutoUpdateInformation(request, response);
    }

    @Override
    public UpdatingStatus setAutomaticUpdatesConfig(Request request, Response response) throws IOException, InterruptedException {
        AutomaticUpdaterConfiguration config = request.getBodyAs(AutomaticUpdaterConfiguration.class);
        List<String> errors = ValidationEngine.validate(config);
        if (errors.isEmpty()) {
            automaticUpdater.setNewConfiguration(config);
            return getAutoUpdateInformation(request, response);
        }
        return null;
    }

    @Override
    public UpdatingStatus setUpdatingStatus(Request request, Response response) throws IOException, InterruptedException {
        UpdatingStatus status = request.getBodyAs(UpdatingStatus.class);
        if (status.isUpdating() && systemUpdater.getUpdateStatus() == State.IDLING) {
            systemUpdater.startUpdate();
            status.setUpdating(true);
        }
        return status;
    }

    @Override
    public UpdatingStatus downloadUpdates(Request request, Response response) throws IOException, InterruptedException {
        UpdatingStatus status = getUpdatingStatus(request, response);

        if (status.isIdling()) {
            systemUpdater.startDownload();
            status.setDownloading(true);
        }
        return status;
    }

    @Override
    public UpdatingStatus getUpdatesCheckStatus(Request request, Response response) throws IOException, InterruptedException {
        Instant now = new Date().toInstant();
        // If there was a request and it was less than a threshold ago...
        if (lastUpdateRequest != null && Duration.between(lastUpdateRequest, now).getSeconds() < secondsBetweenUpdateRequests) {
            // ... avoid too frequent requests
            throw new BadRequestException("Requests too frequent");
        }
        lastUpdateRequest = now;
        try {
            registrationService.checkAndExecuteUpgrade();
        } catch (Exception e) {
            LOG.error("Cannot check for license upgrades", e);
        }

        UpdatingStatus status = getUpdatingStatus(request, response);

        if (status.isIdling()) {
            status.setUpdatesAvailable(systemUpdater.updatesAvailable());
            status.setChecking(true);
        }
        return status;
    }

    private String formatDate(LocalDateTime time) {
        if (time == null)
            return null;

        ZonedDateTime zdt = time.atZone(TimeZone.getDefault().toZoneId());

        String timestamp = zdt.toString();
        if (timestamp.indexOf("[") > 0) {
            timestamp = timestamp.substring(0, timestamp.indexOf("["));
        }
        return timestamp;
    }

}
