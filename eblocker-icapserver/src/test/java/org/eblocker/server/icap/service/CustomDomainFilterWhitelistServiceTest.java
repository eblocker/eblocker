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
package org.eblocker.server.icap.service;

import org.eblocker.server.common.blacklist.CollectionFilter;
import org.eblocker.server.common.blacklist.DomainBlacklistService;
import org.eblocker.server.common.blacklist.DomainFilter;
import org.eblocker.server.common.blacklist.Filters;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;

public class CustomDomainFilterWhitelistServiceTest {

    private DomainBlacklistService domainBlacklistService;
    private UserService userService;
    private UserModule[] users;

    private CustomDomainFilterWhitelistService service;

    @Before
    public void setUp() {
        domainBlacklistService = Mockito.mock(DomainBlacklistService.class);
        Mockito.when(domainBlacklistService.getFilter(0)).thenReturn(new CollectionFilter<>(0, Collections.singleton(".whitelisted.com")));

        users = new UserModule[] {
            createMockUser(0, 0),
            createMockUser(1, null),
            createMockUser(2, 100)
        };
        userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getUserById(0)).thenReturn(users[0]);
        Mockito.when(userService.getUserById(1)).thenReturn(users[1]);
        Mockito.when(userService.getUserById(2)).thenReturn(users[2]);

        service = new CustomDomainFilterWhitelistService(domainBlacklistService, userService);
    }

    @Test
    public void testGetFilter() {
       DomainFilter<String> filter = service.getWhitelistFilter(0);
       Assert.assertNotNull(filter);
       Assert.assertTrue(filter.isBlocked("www.whitelisted.com").isBlocked());
       Assert.assertEquals(Filters.staticFalse(), service.getWhitelistFilter(1));
    }

    @Test
    public void testGetFilterAreCached() {
        DomainFilter<String> filter0 = service.getWhitelistFilter(0);
        DomainFilter<String> filter1 = service.getWhitelistFilter(0);
        Assert.assertSame(filter0, filter1);
    }

    @Test
    public void testGetFilterNoWhitelist() {
        Assert.assertEquals(Filters.staticFalse(), service.getWhitelistFilter(1));
    }

    @Test
    public void testGetFilterNonExistingUser() {
        Assert.assertEquals(Filters.staticFalse(), service.getWhitelistFilter(1));
    }

    @Test
    public void testUpdateUser() {
        Mockito.when(domainBlacklistService.getFilter(1)).thenReturn(new CollectionFilter<>(1, Collections.singleton(".whitelisted-new.com")));
        ArgumentCaptor<UserService.UserChangeListener> captor = ArgumentCaptor.forClass(UserService.UserChangeListener.class);
        Mockito.verify(userService).addListener(captor.capture());

        DomainFilter<String> originalFilter = service.getWhitelistFilter(0);
        Assert.assertNotNull(originalFilter);
        Assert.assertTrue(originalFilter.isBlocked("www.whitelisted.com").isBlocked());
        Assert.assertFalse(originalFilter.isBlocked("www.whitelisted-new.com").isBlocked());

        users[0].setCustomWhitelistId(1);
        captor.getValue().onChange(users[0]);

        DomainFilter<String> updatedFilter = service.getWhitelistFilter(0);
        Assert.assertNotNull(updatedFilter);
        Assert.assertNotSame(originalFilter, updatedFilter);
        Assert.assertFalse(updatedFilter.isBlocked("www.whitelisted.com").isBlocked());
        Assert.assertTrue(updatedFilter.isBlocked("www.whitelisted-new.com").isBlocked());
    }

    @Test
    public void testUpdateBlacklists() {
        ArgumentCaptor<DomainBlacklistService.Listener> captor = ArgumentCaptor.forClass(DomainBlacklistService.Listener.class);
        Mockito.verify(domainBlacklistService).addListener(captor.capture());

        DomainFilter<String> originalFilter = service.getWhitelistFilter(0);
        Assert.assertNotNull(originalFilter);
        Assert.assertTrue(originalFilter.isBlocked("www.whitelisted.com").isBlocked());
        Assert.assertFalse(originalFilter.isBlocked("www.whitelisted-new.com").isBlocked());

        Mockito.when(domainBlacklistService.getFilter(0)).thenReturn(new CollectionFilter<>(0, Collections.singleton(".whitelisted-new.com")));
        captor.getValue().onUpdate();

        DomainFilter<String> updatedFilter = service.getWhitelistFilter(0);
        Assert.assertNotNull(updatedFilter);
        Assert.assertNotSame(originalFilter, updatedFilter);
        Assert.assertFalse(updatedFilter.isBlocked("www.whitelisted.com").isBlocked());
        Assert.assertTrue(updatedFilter.isBlocked("www.whitelisted-new.com").isBlocked());
    }

    // EB1-2393
    @Test
    public void testMissingFilter() {
        Assert.assertEquals(Filters.staticFalse(), service.getWhitelistFilter(2));
    }

    private UserModule createMockUser(int id, Integer whitelistId) {
        return new UserModule(id, null, null, null, null, null, false, null, null, null, null, whitelistId);
    }

}
