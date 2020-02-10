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
package org.eblocker.server.common.data.dns;

import org.junit.Assert;
import org.junit.Test;

public class ResolverEventTest {

    @Test
    public void testParsingEventWithDuration() {
        ResolverEvent event = new ResolverEvent("1511793679.0166283,77.109.148.136,valid,0.030417463");
        Assert.assertEquals(1511793679016L, event.getInstant().toEpochMilli());
        Assert.assertEquals("77.109.148.136", event.getNameServer());
        Assert.assertEquals("valid", event.getStatus());
        Assert.assertEquals(Long.valueOf(30L), event.getDuration());
    }

    @Test
    public void testParsingEventWithoutDuration() {
        ResolverEvent event = new ResolverEvent("1511793678.9852345,91.239.100.100,timeout");
        Assert.assertEquals(1511793678985L, event.getInstant().toEpochMilli());
        Assert.assertEquals("91.239.100.100", event.getNameServer());
        Assert.assertEquals("timeout", event.getStatus());
        Assert.assertNull(event.getDuration());
    }

}
