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
package org.eblocker.server.common.scheduler;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.network.ProblematicRouterDetection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ScheduledFuture;

public class ProblematicRouterDetectionScheduler extends FixedRateScheduler implements Observer {
    private static final Logger log = LoggerFactory.getLogger(ProblematicRouterDetectionScheduler.class);

    private ScheduledFuture<?> problematicRouterDetectionFuture;

    @Inject
    public ProblematicRouterDetectionScheduler(ProblematicRouterDetection problematicRouterDetection,
                                               @Named("executor.problematicRouterDetection.startupDelay") long startupDelay,
                                               @Named("executor.problematicRouterDetection.fixedRate") long fixedRate) {
        super(problematicRouterDetection, startupDelay, fixedRate);

        problematicRouterDetection.addObserver(this); // observe the router detector for successful detections
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof ProblematicRouterDetection) {
            // A problematic (or unproblematic) router was detected
            log.info("Problematic router detection was successful...Stopping it!");
            problematicRouterDetectionFuture.cancel(false);
            problematicRouterDetectionFuture = null;
        }
    }

}
