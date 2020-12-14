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
package org.eblocker.server.icap.filter.bpjm;

import org.junit.Assert;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class BpjmFilterTest {

    public static byte[] md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return digest.digest(value.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("implementation error", e);
        }
    }

    @Test
    public void test() {
        List<BpjmEntry> entries = new ArrayList<>();
        entries.add(createHashEntry("9705f362a3c1460f39f4d38f7335bc89", "d41d8cd98f00b204e9800998ecf8427e", 0));
        entries.add(createHashEntry("332fd094536029d091afa7508406377e", "d41d8cd98f00b204e9800998ecf8427e", 0));
        entries.add(createHashEntry("1dbb8511245e6d42dd433d7fd0d6b8e6", "7ff7b496dacd5b5a012246388dc13f83", 2));
        entries.add(createHashEntry("8ad645c1420bffcc9ce847a7ff0a1467", "7fad013ccf9b96097677ad6482892410", 1));
        entries.add(createHashEntry("8ad645c1420bffcc9ce847a7ff0a1467", "5cb5d18ac0dad08948cf875d7a18cb8a", 1));
        entries.add(createHashEntry("8ad645c1420bffcc9ce847a7ff0a1467", "58c1a624a59e24ceb0991e65f5787158", 1));
        entries.add(createHashEntry("8ad645c1420bffcc9ce847a7ff0a1467", "b52f1d52f567a400a803cacd6418f088", 0));
        entries.add(createEntry("http://youtube.com", "watch?v=4HuJK5nITyo", 0));
        entries.add(createEntry("https://youtube.com", "watch?v=55x-mTrXLto", 0));
        entries.add(createEntry("https://xkcd.com", "2048", 0));

        BpjmFilter filter = new BpjmFilter(new BpjmModul(entries, 0));

        Assert.assertTrue(filter.isBlocked("http://www.internauten.de").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://bpjm.fsm.de").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://bundespruefstelle.de/bpjm/Service/").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://www.fsm.de/en/").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://www.fsm.de/template.gfx/").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://www.fsm.de/de/Mitglieder").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://www.fsm.de/inhalt.gfx").isBlocked());

        Assert.assertFalse(filter.isBlocked("http://www.fsm.de/inhalt.gfx/").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://www.fsm.de/template.gfx/test.png").isBlocked());
        Assert.assertFalse(filter.isBlocked("http://www.fsm.de/de/Mitglieder/hello").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://bundespruefstelle.de/bpjm/Service/test").isBlocked());
        Assert.assertFalse(filter.isBlocked("https://youtube.com").isBlocked());
        Assert.assertFalse(filter.isBlocked("https://youtube.com/watch").isBlocked());
        Assert.assertFalse(filter.isBlocked("https://youtube.com/watch?v=4XUGGVx3PxY").isBlocked());
        Assert.assertTrue(filter.isBlocked("https://youtube.com/watch?v=4HuJK5nITyo").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://youtube.com/watch?v=55x-mTrXLto").isBlocked());
        Assert.assertTrue(filter.isBlocked("https://youtube.com/watch?v=55x-mTrXLto").isBlocked());
        Assert.assertTrue(filter.isBlocked("http://xkcd.com/2048").isBlocked());
        Assert.assertTrue(filter.isBlocked("https://xkcd.com/2048").isBlocked());
    }

    private BpjmEntry createHashEntry(String domainHash, String pathHash, int depth) {
        return new BpjmEntry(DatatypeConverter.parseHexBinary(domainHash), DatatypeConverter.parseHexBinary(pathHash), depth);
    }

    private BpjmEntry createEntry(String domain, String path, int depth) {
        return new BpjmEntry(md5(domain), md5(path), depth);
    }

}
