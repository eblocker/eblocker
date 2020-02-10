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
package org.eblocker.server.common.util;

import org.junit.Assert;
import org.junit.Test;

public class ByteArraysTest {

    @Test
    public void compare() {
        Assert.assertEquals(-1, ByteArrays.compare(new byte[] { 4, 5, 6 }, new byte[] { 1, 2, 3, 4 }));
        Assert.assertEquals(1, ByteArrays.compare(new byte[] { 1, 2, 3, 4 }, new byte[] { 4, 5, 6 } ));
        Assert.assertEquals(-1, ByteArrays.compare(new byte[] { 3, 5, 6 }, new byte[] { 4, 5, 6 }));
        Assert.assertEquals(-1, ByteArrays.compare(new byte[] { 4, 4, 6 }, new byte[] { 4, 5, 6 }));
        Assert.assertEquals(-1, ByteArrays.compare(new byte[] { 4, 5, 5 }, new byte[] { 4, 5, 6 }));
        Assert.assertEquals(0, ByteArrays.compare(new byte[] { 4, 5, 6 }, new byte[] { 4, 5, 6 }));
        Assert.assertEquals(1, ByteArrays.compare(new byte[] { 4, 5, 7 }, new byte[] { 4, 5, 6 }));
        Assert.assertEquals(1, ByteArrays.compare(new byte[] { 4, 7, 6 }, new byte[] { 4, 5, 6 }));
        Assert.assertEquals(1, ByteArrays.compare(new byte[] { 5, 5, 6 }, new byte[] { 4, 5, 6 }));
    }

    @Test
    public void compareOffset() {
        byte[] a = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        byte[] b = { 1, 2, 3, 4, 5 };
        byte[] c = { 0, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 0, 0, 0, 0, 0, 0 };

        Assert.assertEquals(-1, ByteArrays.compare(5, 0, a, b));
        Assert.assertEquals(0, ByteArrays.compare(5, 1, a, b));
        Assert.assertEquals(1, ByteArrays.compare(5, 2, a, b));
        Assert.assertEquals(-1, ByteArrays.compare(5, 10, a, b));
        Assert.assertEquals(0, ByteArrays.compare(5, 11, a, b));
        Assert.assertEquals(1, ByteArrays.compare(5, 12, a, b));

        Assert.assertEquals(-1, ByteArrays.compare(3, 0, c, b));
        Assert.assertEquals(0, ByteArrays.compare(3, 1, c, b));
        Assert.assertEquals(1, ByteArrays.compare(3, 2, c, b));
        Assert.assertEquals(-1, ByteArrays.compare(3, 10, c, b));
        Assert.assertEquals(0, ByteArrays.compare(3, 11, c, b));
        Assert.assertEquals(1, ByteArrays.compare(3, 12, c, b));
    }

    @Test
    public void keys() {
        ByteArrays.Key a = new ByteArrays.Key(new byte[] { 1, 2, 3, 4 });
        ByteArrays.Key b = new ByteArrays.Key(new byte[] { 4, 5, 6 });
        ByteArrays.Key c = new ByteArrays.Key(new byte[] { 7, 8, 9 });

        Assert.assertEquals(new ByteArrays.Key(new byte[] { 1, 2, 3, 4 }), a);
        Assert.assertEquals(a, a);
        Assert.assertNotEquals(a, b);
        Assert.assertNotEquals(a, c);
        Assert.assertEquals(new ByteArrays.Key(new byte[] { 4, 5, 6 }), b);
        Assert.assertEquals(b, b);
        Assert.assertNotEquals(b, c);
        Assert.assertEquals(new ByteArrays.Key(new byte[] { 7, 8, 9 }), c);
        Assert.assertEquals(c, c);

        Assert.assertNotEquals(a.hashCode(), b.hashCode());
        Assert.assertNotEquals(b.hashCode(), c.hashCode());
    }
}
