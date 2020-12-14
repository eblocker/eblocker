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

public class EasyListTrackingFilterTest extends FilterParserTestBase {
    @SuppressWarnings("unused")
    private final static Logger log = LoggerFactory.getLogger(EasyListTrackingFilterTest.class);

    @Override
    protected InputStream[] getInputStreams() {
        return new InputStream[]{
                ResourceHandler.getInputStream(new SimpleResource("classpath:test-data/filter/easyprivacy.txt")),
        };
    }

	/*
2015-02-09 08:57:50,508 [DEBUG] org.eblocker.server.icap.filter.learning.FilterRequestQueue [Thread-1] - Processing filter queue for http://www.google.de/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=0CCMQFjAA&url=http%3A%2F%2Fwww.trustcenter.de%2F&ei=ZmjYVMbNLIviywOBioJo&usg=AFQjCNE239d1NkWZcECxqLe9akXtrfgNlA&sig2=V5izHWZtFGWYi7KEI2IYIw&bvm=bv.85464276,d.bGQ
2015-02-09 08:57:50,508 [DEBUG] org.eblocker.server.icap.filter.learning.FilterRequestQueue [Thread-1] - Found matching filter in queue 1	35730	MEDIUM	@@|http://*.de^$image,third-party,domain=gamona.de	PASS	REGEX[^http://.*\.de([^a-zA-Z0-9_.%-]|$).*$]

	 */

    @Test
    public void testEasyListFilter() {
        String url = "https://www.google.com/gen_204?oq=offscreen%20colonies&gs_l=youtube.3..0.766.10244.0.10588.26.14.3.8.8.1.348.2051.6j5j1j2.14.0....0...1ac.1.23.youtube..5.21.1304.1fl8kePxGC4";
        String referrer = "https://www.youtube.com/";
        String definition = "||google.*/gen_204?$~xmlhttprequest";
        assertFilterResult_beforeLearning(Decision.NO_DECISION, null, url, referrer);
        assertFilterResult_afterLearning(Decision.BLOCK, definition, url, referrer);
    }

    @Test
    public void testEasyListFilter_3() {
        String url = "https://www.google.com/gen_204?oq=offscreen%20colonies&gs_l=youtube.3..0.766.10244.0.10588.26.14.3.8.8.1.348.2051.6j5j1j2.14.0....0...1ac.1.23.youtube..5.21.1304.1fl8kePxGC4";
        String referrer = "http://www.gamona.de";
        String definition = null;
        assertFilterResult_beforeLearning(Decision.NO_DECISION, null, url, referrer);
        assertFilterResult_afterLearning(Decision.BLOCK, definition, url, referrer);
    }

}
