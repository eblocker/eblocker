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

import static org.junit.Assert.*;

import org.junit.Test;

public class RecordedSSLHandshakeTest {

    @Test
    public void parseAndUpdate() {
        // Test lines with too little data are not parsed
        RecordedSSLHandshake handshake = RecordedSSLHandshake.parse("1\t2\t3");
        assertNull(handshake);

        handshake = RecordedSSLHandshake
                .parse("1.2.3.4\t2001:0bd8:85a3:08d3:1319:a82e:0370:7344\twww.server.name\t4711");
        assertNotNull(handshake);
        assertEquals("1.2.3.4", handshake.getIP());
        assertEquals("www.server.name", handshake.getServername());
        assertEquals(4711, handshake.getTCPStreamNumber());
        assertFalse(handshake.isCorrespondingAppDataRecorded());

    }

    @Test
    public void update() {
        RecordedSSLHandshake handshake = RecordedSSLHandshake
                .parse("1.2.3.4\t2001:0bd8:85a3:08d3:1319:a82e:0370:7344\twww.server.name\t4711");
        assertNotNull(handshake);
        // Try to update with mismatching app data
        RecordedSSLAppData mismatchStream = new RecordedSSLAppData("1.2.3.4", 4712);
        handshake.updateWithAppData(mismatchStream);
        assertEquals("1.2.3.4", handshake.getIP());
        assertEquals("www.server.name", handshake.getServername());
        assertEquals(4711, handshake.getTCPStreamNumber());
        assertFalse(handshake.isCorrespondingAppDataRecorded());

        RecordedSSLAppData mismatchIp = new RecordedSSLAppData("1.2.3.5", 4711);
        handshake.updateWithAppData(mismatchIp);
        assertEquals("1.2.3.4", handshake.getIP());
        assertEquals("www.server.name", handshake.getServername());
        assertEquals(4711, handshake.getTCPStreamNumber());
        assertFalse(handshake.isCorrespondingAppDataRecorded());

        // Try to update with matching app data
        RecordedSSLAppData match = new RecordedSSLAppData("1.2.3.4", 4711);
        handshake.updateWithAppData(match);
        assertEquals("1.2.3.4", handshake.getIP());
        assertEquals("www.server.name", handshake.getServername());
        assertEquals(4711, handshake.getTCPStreamNumber());
        assertTrue(handshake.isCorrespondingAppDataRecorded());
    }
}
