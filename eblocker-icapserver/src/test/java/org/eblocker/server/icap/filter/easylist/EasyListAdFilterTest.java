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

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.icap.filter.FilterParserTestBase;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class EasyListAdFilterTest extends FilterParserTestBase {
    @SuppressWarnings("unused")
    private final static Logger log = LoggerFactory.getLogger(EasyListAdFilterTest.class);

    @Override
    protected InputStream[] getInputStreams() {
        return new InputStream[]{
            ResourceHandler.getInputStream(new SimpleResource("classpath:test-data/filter/easylist.txt"))
        };
    }

    @Test
    public void testEasyListFilter_1() {
        String url = "http://www.google.com/uds/afs?q=Tablets%20und%20E-Book%20Reader%20Samsung%20Galaxy%20Tab%204%20SM-T530%2016GB%2C%20WLAN%2C%2025%2C7%20cm%20(10%2C1%20Zoll)%20-%20Schwarz%20(aktuellstes%20Modell)&oe=utf8&ie=utf8&r=m&fexp=21404%2C7000107%2C2631660&client=dealtime-de&channel=40002%2B40016%2B40026%2B40277%2B40005&hl=de&type=0&jsei=3&format=n5&ad=n5&nocache=6491422256053002&num=0&output=uds_ads_only&v=3&bsl=8&u_his=1&u_tz=60&dt=1422256053003&u_w=1920&u_h=1080&biw=1905&bih=955&psw=1905&psh=3174&frm=0&uio=uv3cs1st15sd12sv12-&rurl=http%3A%2F%2Fde.shopping.com%2Fsamsung_galaxy_tab_4_10_1_16gb_wlan_25_7_cm_10_1_zoll_schwarz%2Finfo%3Fsb%3D1#master-1";
        String referrer = "http://www.example.com";

        assertFilterResult_beforeLearning(Decision.NO_DECISION, null, url, referrer);
        assertFilterResult_afterLearning(Decision.BLOCK, "_ads_only&", url, referrer);
    }

    @Test
    public void testEasyListFilter_2() {
        String url = "http://www.google.de/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=0CCMQFjAA&url=http%3A%2F%2Fwww.trustcenter.de%2F&ei=qKjdVNjhEoOYPKXggPgB&usg=AFQjCNE239d1NkWZcECxqLe9akXtrfgNlA&sig2=9lWZM68yvdD7R8c9JvCanw&bvm=bv.85970519,d.ZWU";
        String referrer = "http://www.example.com";

        assertFilterResult_beforeLearning(Decision.NO_DECISION, null, url, referrer);
        assertFilterResult_afterLearning(Decision.NO_DECISION, null, url, referrer);
    }

    @Test
    public void testEasyListFilter_3() {
        String url = "http://www.google.de/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=0CCMQFjAA&url=http%3A%2F%2Fwww.trustcenter.de%2F&ei=qKjdVNjhEoOYPKXggPgB&usg=AFQjCNE239d1NkWZcECxqLe9akXtrfgNlA&sig2=9lWZM68yvdD7R8c9JvCanw&bvm=bv.85970519,d.ZWU";
        String referrer = null;

        assertFilterResult_beforeLearning(Decision.NO_DECISION, null, url, referrer);
        assertFilterResult_afterLearning(Decision.NO_DECISION, null, url, referrer);
    }

}
