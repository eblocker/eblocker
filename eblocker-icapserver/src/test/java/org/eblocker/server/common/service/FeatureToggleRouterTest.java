/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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

import org.eblocker.server.common.data.DataSource;
import org.junit.Before;
import org.junit.Test;

import org.junit.Assert;
import org.mockito.Mockito;

public class FeatureToggleRouterTest {
    private FeatureToggleRouter featureToggleRouter;
    private DataSource dataSource;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
    }

    @Test
    public void testRouterAdvertisements() {
        setFlags(true, true);
        Assert.assertTrue(featureToggleRouter.shouldSendRouterAdvertisements());

        setFlags(true, false);
        Assert.assertFalse(featureToggleRouter.shouldSendRouterAdvertisements());

        setFlags(false, true);
        Assert.assertFalse(featureToggleRouter.shouldSendRouterAdvertisements());
    }

    private void setFlags(boolean ip6Enabled, boolean rasEnabled) {
        Mockito.when(dataSource.areRouterAdvertisementsEnabled()).thenReturn(rasEnabled);
        featureToggleRouter = new FeatureToggleRouter(dataSource, ip6Enabled);
    }
}
