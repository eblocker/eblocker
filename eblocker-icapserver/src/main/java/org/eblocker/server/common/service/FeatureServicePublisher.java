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
package org.eblocker.server.common.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.CompressionMode;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;

/**
 * Service class to manage some simple eBlocker feature settings.
 */
@Singleton
public class FeatureServicePublisher implements FeatureService {

    private final DataSource dataSource;

    private final PubSubService pubSubService;

    @Inject
    public FeatureServicePublisher(DataSource dataSource, PubSubService pubSubService) {
        this.dataSource = dataSource;
        this.pubSubService = pubSubService;
    }

    @Override
    public boolean getWebRTCBlockingState() {
        return dataSource.getWebRTCBlockingState();
    }

    public void setWebRTCBlockingState(boolean webRTCBlockingState) {
        dataSource.setWebRTCBlockingState(webRTCBlockingState);
        triggerReload();
    }

    @Override
    public boolean getHTTPRefererRemovingState() {
        return dataSource.getHTTPRefererRemovingState();
    }

    public void setHTTPRefererRemovingState(boolean httpRefererRemovingState) {
        dataSource.setHTTPRefererRemovingState(httpRefererRemovingState);
        triggerReload();
    }

    @Override
    public boolean getGoogleCaptivePortalRedirectorState() {
        return dataSource.getGoogleCaptivePortalRedirectorState();
    }

    public void setGoogleCaptivePortalRedirectorState(boolean googleCaptivePortalRedirectorState) {
        dataSource.setGoogleCaptivePortalRedirectorState(googleCaptivePortalRedirectorState);
        triggerReload();
    }

    @Override
    public CompressionMode getCompressionMode() {
        return dataSource.getCompressionMode();
    }

    public void setCompressionMode(CompressionMode compressionMode) {
        dataSource.setCompressionMode(compressionMode);
        triggerReload();
    }

    private void triggerReload() {
        pubSubService.publish(Channels.FEATURES_IN, FeatureServiceMessage.RELOAD.name());
    }

    public boolean getDntHeaderState() {
        return dataSource.getDntHeaderState();
    }

    public void setDntHeaderState(boolean state) {
        dataSource.setDntHeaderState(state);
        triggerReload();
    }

}
