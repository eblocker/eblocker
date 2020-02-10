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
package org.eblocker.server.common.blocker;

import org.junit.Assert;
import org.junit.Test;

public class BlockerIdTypeIdCacheTest {

    @Test
    public void test() {
        BlockerIdTypeIdCache idCache = new BlockerIdTypeIdCache();

        int id = idCache.getId(new TypeId(Type.DOMAIN, 100));
        Assert.assertEquals(0, id);
        Assert.assertEquals(new TypeId(Type.DOMAIN, 100), idCache.getTypeId(0));

        int id2 = idCache.getId(new TypeId(Type.PATTERN, 100));
        Assert.assertEquals(1, id2);
        Assert.assertEquals(new TypeId(Type.PATTERN, 100), idCache.getTypeId(1));
        Assert.assertEquals(new TypeId(Type.DOMAIN, 100), idCache.getTypeId(0));
    }

}
