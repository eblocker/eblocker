/*
 * Copyright 2022 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Ip6AddressDelayedValidatorTest {
    private Ip6AddressDelayedValidator validator;
    private ScheduledExecutorService executorService;
    private FeatureToggleRouter featureToggleRouter;
    private NetworkInterfaceWrapper networkInterface;
    private PubSubService pubSubService;

    @Before
    public void setUp() throws Exception {
        executorService = Mockito.mock(ScheduledExecutorService.class);
        featureToggleRouter = Mockito.mock(FeatureToggleRouter.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        pubSubService = Mockito.mock(PubSubService.class);

        Mockito.when(networkInterface.getHardwareAddress()).thenReturn(new byte[] {1, 2, 3, 4, 5, 6});
        Mockito.when(networkInterface.getIp6LinkLocalAddress()).thenReturn(Ip6Address.parse("fe80::1234"));

        validator = new Ip6AddressDelayedValidator(executorService, featureToggleRouter, networkInterface, pubSubService);
    }

    @Test
    public void testValidate() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);
        validator.validateDelayed("abcdef012345", Ip6Address.parse("2001::4711"));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).scheduleWithFixedDelay(captor.capture(), Mockito.eq(5L), Mockito.eq(10L), Mockito.eq(TimeUnit.SECONDS));
        captor.getValue().run();
        Mockito.verify(pubSubService).publish("ip6:out", "010203040506/fe800000000000000000000000001234/abcdef012345/20010000000000000000000000004711/icmp6/135/20010000000000000000000000004711/1/010203040506");
    }

    @Test
    public void testScheduleOnlyOnce() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(true);
        validator.validateDelayed("abcdef012345", Ip6Address.parse("2001::4711"));
        validator.validateDelayed("abcdef012345", Ip6Address.parse("2001::4711"));
        Mockito.verify(executorService).scheduleWithFixedDelay(Mockito.any(Runnable.class), Mockito.eq(5L), Mockito.eq(10L), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testIp6Disabled() {
        Mockito.when(featureToggleRouter.isIp6Enabled()).thenReturn(false);
        validator.validateDelayed("abcdef012345", Ip6Address.parse("2001::4711"));
        Mockito.verifyNoInteractions(executorService);
    }
}