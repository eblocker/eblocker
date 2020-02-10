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
package org.eblocker.server.common.startup;

import org.eblocker.server.common.data.systemstatus.SubSystem;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

public class SubSystemServiceIndexTest {

    @Test
    public void test() {
        Injector injector = Guice.createInjector(new Module());

        SubSystemServiceIndex index = new SubSystemServiceIndex();
        index.scan(injector.getBindings());

        Assert.assertNotNull(index.getRegisteredServices(SubSystem.HTTP_SERVER));
        Assert.assertTrue(index.getRegisteredServices(SubSystem.HTTP_SERVER).isEmpty());

        Assert.assertNotNull(index.getRegisteredServices(SubSystem.DATABASE_CLIENT));
        Assert.assertEquals(1, index.getRegisteredServices(SubSystem.DATABASE_CLIENT).size());
        Assert.assertEquals(DatabaseService.class, index.getRegisteredServices(SubSystem.DATABASE_CLIENT).iterator().next());

        Assert.assertNotNull(index.getRegisteredServices(SubSystem.HTTPS_SERVER));
        Assert.assertEquals(1, index.getRegisteredServices(SubSystem.HTTPS_SERVER).size());
        Assert.assertEquals(HttpsServerServiceImpl.class, index.getRegisteredServices(SubSystem.HTTPS_SERVER).iterator().next());

        Assert.assertNotNull(index.getRegisteredServices(SubSystem.EBLOCKER_CORE));
        Assert.assertEquals(3, index.getRegisteredServices(SubSystem.EBLOCKER_CORE).size());
        Iterator<Class<?>> registeredServicesIterator = index.getRegisteredServices(SubSystem.EBLOCKER_CORE).iterator();
        Assert.assertEquals(EblockerCoreServiceHighPriority.class, registeredServicesIterator.next());
        Assert.assertEquals(EblockerCoreServiceDefaultPriority.class, registeredServicesIterator.next());
        Assert.assertEquals(EblockerCoreServiceLowPriority.class, registeredServicesIterator.next());
    }

    private class Module extends AbstractModule {
        @Override
        protected void configure() {
            bind(DatabaseService.class).in(Scopes.SINGLETON);
            bind(HttpServerService.class).to(HttpsServerServiceImpl.class).in(Scopes.SINGLETON);
            bind(NonSubSystemService.class).in(Scopes.SINGLETON);
            bind(EblockerCoreServiceLowPriority.class);
            bind(EblockerCoreServiceDefaultPriority.class);
            bind(EblockerCoreServiceHighPriority.class);
        }
    }

    @SubSystemService(SubSystem.DATABASE_CLIENT)
    public static class DatabaseService {
    }

    public interface HttpServerService {
    }

    @SubSystemService(SubSystem.HTTPS_SERVER)
    public static class HttpsServerServiceImpl implements HttpServerService {
    }

    public static class NonSubSystemService {
    }

    @SubSystemService(value = SubSystem.EBLOCKER_CORE, initPriority = -10)
    public static class EblockerCoreServiceHighPriority {
    }

    @SubSystemService(SubSystem.EBLOCKER_CORE)
    public static class EblockerCoreServiceDefaultPriority {
    }

    @SubSystemService(value = SubSystem.EBLOCKER_CORE, initPriority = 10)
    public static class EblockerCoreServiceLowPriority {
    }
}
