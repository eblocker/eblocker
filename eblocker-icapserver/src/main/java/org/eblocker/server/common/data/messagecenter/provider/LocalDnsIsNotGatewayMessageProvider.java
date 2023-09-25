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
package org.eblocker.server.common.data.messagecenter.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.util.Ip4Utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class LocalDnsIsNotGatewayMessageProvider extends AbstractMessageProvider {

    private final EblockerDnsServer dnsServer;
    private final NetworkServices networkServices;

    @Inject
    public LocalDnsIsNotGatewayMessageProvider(EblockerDnsServer dnsServer, NetworkServices networkServices) {
        this.dnsServer = dnsServer;
        this.networkServices = networkServices;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        NetworkConfiguration configuration = networkServices.getCurrentNetworkConfiguration();
        if (dnsServer.isEnabled()
                && configuration.isAutomatic()
                && !configuration.isDhcp()
                && isLocalDnsServerPresentWhichIsNotGateway(dnsServer.getDhcpNameServers(), configuration.getIpAddress(), configuration.getGateway(), configuration.getNetworkMask())) {
            if (!messageContainers.containsKey(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId())) {
                messageContainers.put(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId(), createMessage());
            }
        } else {
            messageContainers.remove(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId());
        }
    }

    private boolean isLocalDnsServerPresentWhichIsNotGateway(List<String> nameServers, String eblockerIpAddressString, String gatewayIpAddressString, String networkMaskString) {
        int gatewayIpAddress = Ip4Utils.convertIpStringToInt(gatewayIpAddressString);
        int networkMask = Ip4Utils.convertIpStringToInt(networkMaskString);
        int network = gatewayIpAddress & networkMask;
        return nameServers.stream()
                .filter(ns -> Ip4Utils.isIPAddress(ns))
                .filter(ns -> !ns.equals(gatewayIpAddressString))
                .filter(ns -> !ns.equals(eblockerIpAddressString))
                .map(ns -> Ip4Utils.convertIpStringToInt(ns) & network)
                .filter(ns -> ns == network)
                .findAny()
                .isPresent();
    }

    private MessageContainer createMessage() {
        return createMessage(MessageProviderMessageId.MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY.getId(),
                "MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY_TITLE",
                "MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY_CONTENT",
                "MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY_LABEL",
                "MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY_URL",
                Collections.emptyMap(),
                false,
                MessageSeverity.ALERT);
    }
}
