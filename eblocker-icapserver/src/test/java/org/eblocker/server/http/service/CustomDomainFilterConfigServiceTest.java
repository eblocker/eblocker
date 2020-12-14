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

import com.google.common.collect.Sets;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterSummaryData;
import org.eblocker.server.http.model.CustomDomainFilterConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.xml.ws.Holder;
import java.util.Collections;
import java.util.Set;

public class CustomDomainFilterConfigServiceTest {

    private ParentalControlFilterListsService filterListsService;
    private UserService userService;
    private CustomDomainFilterConfigService customDomainFilterConfigService;

    @Before
    public void setUp() {
        Holder<Integer> nextId = new Holder<>(100);
        filterListsService = Mockito.mock(ParentalControlFilterListsService.class);
        Mockito.when(filterListsService.getFilterById(1)).thenReturn(mockMetaData(1, "whitelist"));
        Mockito.when(filterListsService.createFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).then(im -> {
            ParentalControlFilterSummaryData data = im.getArgument(0);
            data.setId(nextId.value);
            nextId.value = nextId.value + 1;
            return data;
        });
        Mockito.when(filterListsService.updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString())).then(im -> im.getArgument(0));
        Mockito.when(filterListsService.getFilterListDomains(1)).thenReturn(Collections.singletonList("eblocker.com"));
        Mockito.when(filterListsService.getFilterById(2)).thenReturn(mockMetaData(2, "blacklist"));
        Mockito.when(filterListsService.getFilterListDomains(2)).thenReturn(Collections.singletonList("etracker.com"));

        userService = Mockito.mock(UserService.class);
        // filter lists are _never_ shared across users, this is just for ease of testing setup
        Mockito.when(userService.getUserById(0)).thenReturn(mockUser(0, null, null));
        Mockito.when(userService.getUserById(1)).thenReturn(mockUser(1, null, 1));
        Mockito.when(userService.getUserById(2)).thenReturn(mockUser(2, 2, null));
        Mockito.when(userService.getUserById(3)).thenReturn(mockUser(3, 2, 1));

        customDomainFilterConfigService = new CustomDomainFilterConfigService(filterListsService, userService);
    }

    @Test
    public void getCustomDomainFilter() {
        assertCustomDomainFilter(Collections.emptySet(), Collections.emptySet(), customDomainFilterConfigService.getCustomDomainFilterConfig(0));
        assertCustomDomainFilter(Collections.emptySet(), Collections.singleton("eblocker.com"), customDomainFilterConfigService
            .getCustomDomainFilterConfig(1));
        assertCustomDomainFilter(Collections.singleton("etracker.com"), Collections.emptySet(), customDomainFilterConfigService
            .getCustomDomainFilterConfig(2));
        assertCustomDomainFilter(Collections.singleton("etracker.com"), Collections.singleton("eblocker.com"), customDomainFilterConfigService
            .getCustomDomainFilterConfig(3));
    }

    @Test
    public void setCustomDomainFilterEmptyNoAction() {
        CustomDomainFilterConfig savedFilter = customDomainFilterConfigService
            .setCustomDomainFilterConfig(0, new CustomDomainFilterConfig(Collections.emptySet(), Collections.emptySet()));
        assertCustomDomainFilter(Collections.emptySet(), Collections.emptySet(), savedFilter);
        Mockito.verifyZeroInteractions(filterListsService);
        Mockito.verify(userService, Mockito.never()).updateUser(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void setCustomDomainFilterEmptyDelete() {
        CustomDomainFilterConfig savedFilter = customDomainFilterConfigService
            .setCustomDomainFilterConfig(3, new CustomDomainFilterConfig(Collections.emptySet(), Collections.emptySet()));
        assertCustomDomainFilter(Collections.emptySet(), Collections.emptySet(), savedFilter);
        Mockito.verify(filterListsService).deleteFilterList(1);
        Mockito.verify(filterListsService).deleteFilterList(2);
        Mockito.verify(userService).updateUser(3, null, null);
    }

    @Test
    public void setCustomDomainFilterUpdate() {
        CustomDomainFilterConfig savedFilter = customDomainFilterConfigService
            .setCustomDomainFilterConfig(3, new CustomDomainFilterConfig(Sets.newHashSet("etracker.com", "google.com"), Sets.newHashSet("eblocker.com", "xkcd.com")));
        assertCustomDomainFilter(Sets.newHashSet("etracker.com", "google.com"), Sets.newHashSet("eblocker.com", "xkcd.com"), savedFilter);

        ArgumentCaptor<ParentalControlFilterSummaryData> captor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).updateFilterList(captor.capture(), Mockito.eq("blacklist"));
        Mockito.verify(filterListsService).updateFilterList(captor.capture(), Mockito.eq("whitelist"));
        Mockito.verify(userService, Mockito.never()).updateUser(Mockito.any(), Mockito.any(), Mockito.any());

        Assert.assertEquals(Integer.valueOf(2), captor.getAllValues().get(0).getId());
        Assert.assertEquals(Sets.newHashSet("etracker.com", "google.com"), Sets.newHashSet(captor.getAllValues().get(0).getDomains()));
        Assert.assertEquals(Category.CUSTOM, captor.getAllValues().get(0).getCategory());
        Assert.assertEquals("blacklist", captor.getAllValues().get(0).getFilterType());
        Assert.assertEquals("custom-name-2", captor.getAllValues().get(0).getCustomerCreatedName());
        Assert.assertEquals("custom-desc-2", captor.getAllValues().get(0).getCustomerCreatedDescription());

        Assert.assertEquals(Integer.valueOf(1), captor.getAllValues().get(1).getId());
        Assert.assertEquals(Sets.newHashSet("eblocker.com", "xkcd.com"), Sets.newHashSet(captor.getAllValues().get(1).getDomains()));
        Assert.assertEquals(Category.CUSTOM, captor.getAllValues().get(1).getCategory());
        Assert.assertEquals("whitelist", captor.getAllValues().get(1).getFilterType());
        Assert.assertEquals("custom-name-1", captor.getAllValues().get(1).getCustomerCreatedName());
        Assert.assertEquals("custom-desc-1", captor.getAllValues().get(1).getCustomerCreatedDescription());
    }

    @Test
    public void setCustomDomainFilterUpdateNoChange() {
        CustomDomainFilterConfig savedFilter = customDomainFilterConfigService.setCustomDomainFilterConfig(3, new CustomDomainFilterConfig(Collections.singleton("etracker.com"), Collections.singleton("eblocker.com")));
        assertCustomDomainFilter(Collections.singleton("etracker.com"), Collections.singleton("eblocker.com"), savedFilter);

        Mockito.verify(filterListsService, Mockito.never()).updateFilterList(Mockito.any(ParentalControlFilterSummaryData.class), Mockito.anyString());
        Mockito.verify(userService, Mockito.never()).updateUser(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void setCustomDomainFilterCreate() {
        CustomDomainFilterConfig savedFilter = customDomainFilterConfigService
            .setCustomDomainFilterConfig(0, new CustomDomainFilterConfig(Sets.newHashSet("etracker.com", "google.com"), Sets.newHashSet("eblocker.com", "xkcd.com")));
        assertCustomDomainFilter(Sets.newHashSet("etracker.com", "google.com"), Sets.newHashSet("eblocker.com", "xkcd.com"), savedFilter);

        ArgumentCaptor<ParentalControlFilterSummaryData> captor = ArgumentCaptor.forClass(ParentalControlFilterSummaryData.class);
        Mockito.verify(filterListsService).createFilterList(captor.capture(), Mockito.eq("blacklist"));
        Mockito.verify(filterListsService).createFilterList(captor.capture(), Mockito.eq("whitelist"));
        Mockito.verify(userService).updateUser(0, 100, 101);

        Assert.assertEquals(Integer.valueOf(100), captor.getAllValues().get(0).getId());
        Assert.assertEquals(Sets.newHashSet("etracker.com", "google.com"), Sets.newHashSet(captor.getAllValues().get(0).getDomains()));
        Assert.assertEquals(Category.CUSTOM, captor.getAllValues().get(0).getCategory());
        Assert.assertEquals("blacklist", captor.getAllValues().get(0).getFilterType());
        Assert.assertEquals("user-0-blacklist", captor.getAllValues().get(0).getCustomerCreatedName());
        Assert.assertEquals("user-0-blacklist", captor.getAllValues().get(0).getCustomerCreatedDescription());

        Assert.assertEquals(Integer.valueOf(101), captor.getAllValues().get(1).getId());
        Assert.assertEquals(Sets.newHashSet("eblocker.com", "xkcd.com"), Sets.newHashSet(captor.getAllValues().get(1).getDomains()));
        Assert.assertEquals(Category.CUSTOM, captor.getAllValues().get(1).getCategory());
        Assert.assertEquals("whitelist", captor.getAllValues().get(1).getFilterType());
        Assert.assertEquals("user-0-whitelist", captor.getAllValues().get(1).getCustomerCreatedName());
        Assert.assertEquals("user-0-whitelist", captor.getAllValues().get(1).getCustomerCreatedDescription());
    }

    private UserModule mockUser(int id, Integer blacklistId, Integer whitelistId) {
        return new UserModule(id, null, null, null, null, null, false, null, null, null, blacklistId, whitelistId);
    }

    private ParentalControlFilterSummaryData mockMetaData(int id, String type) {
        return new ParentalControlFilterSummaryData(id, null, null, null, null, type, false, false, null, "custom-name-" + id, "custom-desc-" + id, Category.CUSTOM);
    }

    private void assertCustomDomainFilter(Set<String> expectedBlacklist, Set<String> expectedWhitelist, CustomDomainFilterConfig customDomainFilterConfig) {
        Assert.assertNotNull(customDomainFilterConfig);
        Assert.assertEquals(expectedBlacklist, customDomainFilterConfig.getBlacklistedDomains());
        Assert.assertEquals(expectedWhitelist, customDomainFilterConfig.getWhitelistedDomains());
    }
}
