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
package org.eblocker.server.common.data;

import org.eblocker.server.icap.resources.DefaultEblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MacPrefixTest {
    private MacPrefix macPrefix;

    @Before
    public void setUp() throws Exception {
        macPrefix = new MacPrefix();
        macPrefix.addInputStream(ResourceHandler.getInputStream(DefaultEblockerResource.MAC_PREFIXES));
    }

    @Test
    public void existing() {
        assertEquals("XEROX CORPORATION", macPrefix.getVendor("000000abcdef"));
        assertEquals("RG Nets, Inc.", macPrefix.getVendor("0023fa000000"));
        assertEquals("杭州德澜科技有限公司（HangZhou Delan Technology Co.,Ltd）", macPrefix.getVendor("3c2c94012345"));
        assertEquals("IEEE Registration Authority", macPrefix.getVendor("fcffaaffffff"));
    }

    @Test
    public void notExisting() {
        assertNull(macPrefix.getVendor("ffffff000000"));
    }

    @Test
    public void nullInput() {
        assertNull(macPrefix.getVendor(null));
    }

    @Test
    public void tooShort() {
        assertNull(macPrefix.getVendor("0123"));
    }
}
