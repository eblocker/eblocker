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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.session.UserAgentInfo;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class UserAgentServiceTest {

    @Test
    public void testGetUserAgentInfo() {
        UserAgentService userAgentService = new UserAgentService(
            Mockito.mock(DataSource.class),
            4,
            "(Mozilla|Opera)/[0-9.]+\\s.*",
            ".*(^|;|\\s)(MSIE |Trident/)[0-9.]+(\\s|;|$).*"

        );

        Map<String, UserAgentInfo> userAgents = new HashMap<>();

        // MSIE
        userAgents.put("Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1.7601.65536; de-DE)", UserAgentInfo.MSIE);
        userAgents.put("Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko", UserAgentInfo.MSIE);
        userAgents.put("Mozilla/5.0 (Windows NT 6.1; Win64; x64; Trident/7.0; rv:11.0) like Gecko", UserAgentInfo.MSIE);

        // Edge
        userAgents.put("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.79 Safari/537.36 Edge/14.14393", UserAgentInfo.OTHER_BROWSER);

        // Other Browsers
        userAgents.put("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:34.0) Gecko/20100101 Firefox/34.0", UserAgentInfo.OTHER_BROWSER);
        userAgents.put("Mozilla/5.0 (Macintosh; U; 68K Mac OS X 10.6; en-US; rv:1.9.0.8) Gecko/1981082500 Firefox/3.0.8", UserAgentInfo.OTHER_BROWSER);
        userAgents.put("Mozilla/5.0 (iPad; CPU OS 8_1_2 like Mac OS X) AppleWebKit/600.1.4 (KHTML, like Gecko)", UserAgentInfo.OTHER_BROWSER);
        userAgents.put("Opera/9.80 (Windows NT 6.1; Win64; x64) Presto/2.12.388 Version/12.16", UserAgentInfo.OTHER_BROWSER);
        userAgents.put("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36", UserAgentInfo.OTHER_BROWSER);
        userAgents.put("Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.38 Safari/537.36", UserAgentInfo.OTHER_BROWSER);

        // Other Apps
        userAgents.put("", UserAgentInfo.OTHER);
        userAgents.put("xzy", UserAgentInfo.OTHER);

        for (Map.Entry<String, UserAgentInfo> entry: userAgents.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue(), userAgentService.getUserAgentInfo(entry.getKey()));
        }
    }

}
