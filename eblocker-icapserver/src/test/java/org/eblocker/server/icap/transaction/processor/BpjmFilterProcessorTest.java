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
package org.eblocker.server.icap.transaction.processor;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.icap.filter.bpjm.BpjmFilter;
import org.eblocker.server.icap.filter.bpjm.BpjmFilterDecision;
import org.eblocker.server.icap.filter.bpjm.BpjmFilterService;
import org.eblocker.server.icap.transaction.Transaction;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Set;

public class BpjmFilterProcessorTest {

    private static final int BPJM_FILTER_ID = 500;
    private static final int OTHER_FILTER_ID = 501;
    private static final String REDIRECT_PAGE = "/access-denied";
    private static final String DEVICE_ID = "device:00112233445566";
    private static final int USER_ID = 1;
    private static final int PROFILE_ID = 2;

    private static final String BLOCKED_URL = "https://www.evil.org/index.html";
    private static final String NON_BLOCKED_URL = "https://www.good.com/index.html";

    private BaseURLs baseUrls;
    private BpjmFilterService bpjmFilterService;
    private DeviceService deviceService;
    private ParentalControlService parentalControlService;
    private UserService userService;

    private BpjmFilterProcessor processor;

    private UserProfileModule profile;

    @Before
    public void setUp() {
        baseUrls = Mockito.mock(BaseURLs.class);
        Mockito.when(baseUrls.selectURLForPage(BLOCKED_URL)).thenReturn("https://eblocker.box");

        bpjmFilterService = Mockito.mock(BpjmFilterService.class);
        Mockito.when(bpjmFilterService.isBlocked(BLOCKED_URL))
            .thenReturn(new BpjmFilterDecision(true, "evil.org", "index.html", 0));
        Mockito.when(bpjmFilterService.isBlocked(NON_BLOCKED_URL)).thenReturn(BpjmFilter.NOT_BLOCKED_DECISION);

        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn(DEVICE_ID);

        Device device = new Device();
        device.setOperatingUser(USER_ID);
        device.setAssignedUser(USER_ID);

        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(device);

        profile = new UserProfileModule(PROFILE_ID, null, null, null, null, null, null, null, Collections.emptySet(),
            UserProfileModule.InternetAccessRestrictionMode.NONE, null, null, null, false, null);
        parentalControlService = Mockito.mock(ParentalControlService.class);
        Mockito.when(parentalControlService.getProfile(PROFILE_ID)).thenReturn(profile);

        UserModule user = new UserModule(USER_ID, PROFILE_ID, null, null, null, null, false, null, null, null, null, null);
        userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getUserById(USER_ID)).thenReturn(user);

        processor = new BpjmFilterProcessor(BPJM_FILTER_ID, REDIRECT_PAGE, baseUrls, bpjmFilterService, deviceService,
            parentalControlService, userService);
    }

    @Test
    public void testBlockedUrlFilteringPatternEnabledAndUrlsDisabled() {
        setUpProfileMock(false, Collections.singleton(BPJM_FILTER_ID));
        Transaction transaction = createTransaction(BLOCKED_URL, true);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verifyZeroInteractions(bpjmFilterService);
        Mockito.verify(transaction, Mockito.never()).redirect(Mockito.anyString());
    }

    @Test
    public void testBlockedUrlFilteringPatternsDisabledAndUrlsEnabled() {
        setUpProfileMock(true, Collections.singleton(BPJM_FILTER_ID));
        Transaction transaction = createTransaction(BLOCKED_URL, false);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verifyZeroInteractions(bpjmFilterService);
        Mockito.verify(transaction, Mockito.never()).redirect(Mockito.anyString());
    }

    @Test
    public void testBlockedUrlFilteringPatternsEnabledAndUrlEnabled() {
        setUpProfileMock(true, Collections.singleton(BPJM_FILTER_ID));
        Transaction transaction = createTransaction(BLOCKED_URL, true);
        Assert.assertFalse(processor.process(transaction));
        Mockito.verify(bpjmFilterService).isBlocked(BLOCKED_URL);
        Mockito.verify(transaction).redirect("https://eblocker.box/access-denied?target=https%3A%2F%2Fwww.evil.org%2Findex.html&listId=500&domain=evil.org&profileId=2&userId=1");
    }

    @Test
    public void testBlockedUrlFilteringEnabledButListInactive() {
        setUpProfileMock(true, Collections.singleton(OTHER_FILTER_ID));
        Transaction transaction = createTransaction(BLOCKED_URL, true);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verifyZeroInteractions(bpjmFilterService);
        Mockito.verify(transaction, Mockito.never()).redirect(Mockito.anyString());
    }

    @Test
    public void testNonBlockedUrlFilteringPatternsDisabled() {
        Transaction transaction = createTransaction(NON_BLOCKED_URL, false);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verifyZeroInteractions(bpjmFilterService);
        Mockito.verify(transaction, Mockito.never()).redirect(Mockito.anyString());
    }

    @Test
    public void testNonBlockedUrlFilteringPatternsEnabled() {
        setUpProfileMock(true, Collections.singleton(BPJM_FILTER_ID));
        Transaction transaction = createTransaction(NON_BLOCKED_URL, true);
        Assert.assertTrue(processor.process(transaction));
        Mockito.verify(bpjmFilterService).isBlocked(NON_BLOCKED_URL);
        Mockito.verify(transaction, Mockito.never()).redirect(Mockito.anyString());
    }

    private void setUpProfileMock(boolean controlModeUrlsEnabled, Set<Integer> blockedListIds) {
        profile.setControlmodeUrls(controlModeUrlsEnabled);
        profile.setInaccessibleSitesPackages(blockedListIds);
    }

    private Transaction createTransaction(String url, boolean patternFiltersEnabled) {
        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getDeviceId()).thenReturn(DEVICE_ID);
        Mockito.when(session.isPatternFiltersEnabled()).thenReturn(patternFiltersEnabled);

        Transaction transaction = Mockito.mock(Transaction.class);
        Mockito.when(transaction.getUrl()).thenReturn(url);
        Mockito.when(transaction.getSession()).thenReturn(session);
        return transaction;
    }

}
