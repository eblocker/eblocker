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

import org.eblocker.server.common.data.CompressionMode;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.pubsub.Channels;
import org.eblocker.server.common.pubsub.PubSubService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.concurrent.Executor;

/**
 * Service class to manage some simple eBlocker feature settings.
 *
 */
@Singleton
public class FeatureServiceSubscriber implements FeatureService {

    private final DataSource dataSource;

    private final PubSubService pubSubService;

    private final Executor executor;

    private boolean webRTCBlockingState;
    private boolean httpRefererRemovingState;
    private boolean googleCaptivePortalRedirectorState;
    private boolean dntHeaderState;
    private CompressionMode compressionMode;

    private class FeatureServiceSubscriberRunner implements Runnable {
        @Override
        public void run() {
            pubSubService.subscribeAndLoop(Channels.FEATURES_IN, message -> execute(FeatureServiceMessage.parse(message)));
        }
    }

    @Inject
    public FeatureServiceSubscriber(
            DataSource dataSource,
            PubSubService pubSubService,
            @Named("unlimitedCachePoolExecutor") Executor executor
    ) {
        this.dataSource = dataSource;
        this.pubSubService = pubSubService;
        this.executor = executor;
        init();
    }

    private void init() {
        executor.execute(new FeatureServiceSubscriberRunner());
        reload();
    }

    private void execute(FeatureServiceMessage message) {
        switch (message) {
            case RELOAD:
            default:
                reload();
        }
    }

    public void reload() {
        webRTCBlockingState = dataSource.getWebRTCBlockingState();
        httpRefererRemovingState = dataSource.getHTTPRefererRemovingState();
        googleCaptivePortalRedirectorState = dataSource.getGoogleCaptivePortalRedirectorState();
        dntHeaderState = dataSource.getDntHeaderState();
        compressionMode = dataSource.getCompressionMode();
    }

    @Override
    public boolean getWebRTCBlockingState() {
        return webRTCBlockingState;
    }

    @Override
    public boolean getHTTPRefererRemovingState() {
        return httpRefererRemovingState;
    }

    @Override
    public boolean getGoogleCaptivePortalRedirectorState() {
        return googleCaptivePortalRedirectorState;
    }

    @Override
    public boolean getDntHeaderState() {
        return dntHeaderState;
    }

    @Override
    public CompressionMode getCompressionMode() {
        return compressionMode;
    }
}
