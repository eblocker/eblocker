/*
 * Copyright 2025 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.system;

import org.eblocker.server.common.exceptions.EblockerException;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SystemRequirementsTest {

    @Test
    public void defaultConfigAllowsLowHeap384And128Mb() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("configuration.properties")) {
            Assert.assertNotNull(in);
            props.load(in);
        }
        int required = Integer.parseInt(props.getProperty("system.requirement.heap_size_mb").trim());
        Assert.assertTrue("required heap threshold must allow -Xmx384m and -Xmx128m", required <= 128);
        new SystemRequirements(required, props.getProperty("system.requirement.total_memory")).check();
    }

    @Test(expected = EblockerException.class)
    public void throwsWhenMaxHeapBelowRequiredThreshold() {
        long aboveCurrentMax = (Runtime.getRuntime().maxMemory() / 1048576L) + 64L;
        new SystemRequirements((int) aboveCurrentMax, "higher").check();
    }
}
