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
import org.eblocker.server.common.executor.RetryingRunnable;
import org.eblocker.server.common.network.Ip6RouterAdvertiser;

public class Ip6RouterAdvertiserScheduler extends FixedRateScheduler {

    @Inject
    public Ip6RouterAdvertiserScheduler(@Named("executor.ip6RouterAdvertisement.startupDelay") long initialDelay,
                                        @Named("executor.ip6RouterAdvertisement.fixedRate") long fixedRate,
                                        @Named("executor.ip6RouterAdvertisement.maxRetries") long maxRetries,
                                        Ip6RouterAdvertiser ip6RouterAdvertiser) {
        super(new RetryingRunnable(ip6RouterAdvertiser::advertise, maxRetries), initialDelay, fixedRate);
    }
}
