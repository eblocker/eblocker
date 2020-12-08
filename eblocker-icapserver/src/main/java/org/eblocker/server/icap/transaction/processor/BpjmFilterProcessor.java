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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.icap.filter.bpjm.BpjmFilterDecision;
import org.eblocker.server.icap.filter.bpjm.BpjmFilterService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;

@Singleton
public class BpjmFilterProcessor implements TransactionProcessor {

    private final int filterId;
    private final String redirectPage;
    private final BaseURLs baseUrls;
    private final BpjmFilterService bpjmFilterService;
    private final DeviceService deviceService;
    private final ParentalControlService parentalControlService;
    private final UserService userService;

    @Inject
    public BpjmFilterProcessor(@Named("parentalcontrol.bpjm.filter.id") int filterId,
                               @Named("parentalControl.redirectPage") String redirectPage,
                               BaseURLs baseUrls,
                               BpjmFilterService bpjmFilterService,
                               DeviceService deviceService,
                               ParentalControlService parentalControlService,
                               UserService userService) {
        this.filterId = filterId;
        this.redirectPage = redirectPage;
        this.baseUrls = baseUrls;
        this.bpjmFilterService = bpjmFilterService;
        this.deviceService = deviceService;
        this.parentalControlService = parentalControlService;
        this.userService = userService;
    }

    @Override
    public boolean process(Transaction transaction) {
        Session session = transaction.getSession();
        if (!session.isPatternFiltersEnabled()) {
            return true;
        }

        UserModule user = getUser(session);
        UserProfileModule profile = parentalControlService.getProfile(user.getAssociatedProfileId());
        if (!profile.isControlmodeUrls()) {
            return true;
        }
        if (!profile.getInaccessibleSitesPackages().contains(filterId)) {
            return true;
        }

        String url = transaction.getUrl();
        BpjmFilterDecision decision = bpjmFilterService.isBlocked(url);
        if (decision.isBlocked()) {
            transaction.redirect(baseUrls.selectURLForPage(url)
                + redirectPage
                + "?target=" + UrlUtils.urlEncode(url)
                + "&listId=" + filterId
                + "&domain=" + decision.getDomain()
                + "&profileId=" + profile.getId()
                + "&userId=" + user.getId());
            return false;
        }
        return true;
    }

    private UserModule getUser(Session session) {
        Device device = deviceService.getDeviceById(session.getDeviceId());
        UserModule user = userService.getUserById(device.getOperatingUser());
        return user != null ? user : userService.getUserById(device.getAssignedUser());
    }
}
