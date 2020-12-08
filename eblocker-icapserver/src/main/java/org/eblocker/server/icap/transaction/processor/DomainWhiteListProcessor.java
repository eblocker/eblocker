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

import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.WhiteListConfig;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;

import javax.inject.Inject;
import javax.inject.Singleton;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class DomainWhiteListProcessor implements TransactionProcessor {

    private final DeviceService deviceService;
    private final UserService userService;

    @Inject
    public DomainWhiteListProcessor(DeviceService deviceService, UserService userService) {
        this.deviceService = deviceService;
        this.userService = userService;
    }

    @Override
    public boolean process(Transaction transaction) {
        UserModule user = getUser(transaction.getSession());
        String domain = transaction.getDomain();
        WhiteListConfig whiteListConfig = getWhiteListConfig(user, domain);
        transaction.getPageContext().setWhiteListConfig(whiteListConfig);
        return true;
    }

    // TODO: move this to session to avoid to avoid loading users by device in multiple processors?
    private UserModule getUser(Session session) {
        Device device = deviceService.getDeviceById(session.getDeviceId());
        UserModule user = userService.getUserById(device.getOperatingUser());
        if (user == null) {
            user = userService.getUserById(device.getAssignedUser());
        }
        return user;
    }

    private WhiteListConfig getWhiteListConfig(UserModule user, String domain) {
        WhiteListConfig config = user.getWhiteListConfigByDomains().get(domain);
        return config != null ? config : WhiteListConfig.noWhiteListing();
    }
}
