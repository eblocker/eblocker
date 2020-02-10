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
package org.eblocker.server.icap.filter.easylist;

import org.junit.Assert;
import org.junit.Test;

public class EasyListLineParserUtilsTest {

    @Test
    public void test() {
        Assert.assertEquals("007-gateway.com", EasyListLineParserUtils.findDomain("007-gateway.com^$third-party"));
        Assert.assertEquals("banner.t-online.de", EasyListLineParserUtils.findDomain("banner.t-online.de/apps/$object-subrequest,domain=autos.t-online.de"));
        Assert.assertEquals("cacheserve.*", EasyListLineParserUtils.findDomain("cacheserve.*/promodisplay/"));
        Assert.assertEquals("*.com", EasyListLineParserUtils.findDomain("com/banners/"));
        Assert.assertEquals("fırstrowsports.eu", EasyListLineParserUtils.findDomain("fırstrowsports.eu/pu/"));
        Assert.assertEquals(null, EasyListLineParserUtils.findDomain("firstrow*/pu.js"));
        Assert.assertEquals("tracking.sportsbet.*", EasyListLineParserUtils.findDomain("tracking.sportsbet.$popup,third-party"));
    }

}
