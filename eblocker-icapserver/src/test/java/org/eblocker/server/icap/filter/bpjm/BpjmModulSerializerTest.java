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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class BpjmModulSerializerTest {

    @Test
    public void test() throws IOException {
        BpjmModul bpjmModul = new BpjmModul(Arrays.asList(
                new BpjmEntry(BpjmFilterTest.md5("domain"), BpjmFilterTest.md5(""), 0),
                new BpjmEntry(BpjmFilterTest.md5("domain"), BpjmFilterTest.md5("path"), 0),
                new BpjmEntry(BpjmFilterTest.md5("domain"), BpjmFilterTest.md5("path/"), 1),
                new BpjmEntry(BpjmFilterTest.md5("anotherdomain"), BpjmFilterTest.md5("path/file"), 2)
        ), System.currentTimeMillis());

        ByteArrayOutputStream serializedOut = new ByteArrayOutputStream();
        BpjmModulSerializer serializer = new BpjmModulSerializer();
        serializer.write(bpjmModul, serializedOut);

        BpjmModul deserializedBpjmModul = serializer.read(new ByteArrayInputStream(serializedOut.toByteArray()));
        Assert.assertNotNull(deserializedBpjmModul);
        Assert.assertEquals(bpjmModul.getLastModified(), deserializedBpjmModul.getLastModified());
        Assert.assertNotNull(deserializedBpjmModul.getEntries());
        Assert.assertEquals(bpjmModul.getEntries().size(), deserializedBpjmModul.getEntries().size());
        for (int i = 0; i < bpjmModul.getEntries().size(); ++i) {
            Assert.assertArrayEquals(bpjmModul.getEntries().get(i).getDomainHash(), deserializedBpjmModul.getEntries().get(i).getDomainHash());
            Assert.assertArrayEquals(bpjmModul.getEntries().get(i).getPathHash(), deserializedBpjmModul.getEntries().get(i).getPathHash());
            Assert.assertEquals(bpjmModul.getEntries().get(i).getDepth(), deserializedBpjmModul.getEntries().get(i).getDepth());
        }
    }

}
