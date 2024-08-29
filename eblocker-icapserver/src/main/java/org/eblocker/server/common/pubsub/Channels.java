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
package org.eblocker.server.common.pubsub;

/**
 * in/out from the ICAP server's point of view
 */
public class Channels {
    public static final String ARP_IN = "arp:in";
    public static final String ARP_OUT = "arp:out";
    public static final String DHCP_IN = "dhcp:in";
    public static final String DHCP_IP_IN = "dhcp_ip:in";
    public static final String VPN_PROFILE_STATUS_IN = "vpn_profile_status:%s:in";
    public static final String FEATURES_IN = "features:in";
    public static final String VPN_ADDRESS_UPDATE = "vpn_address_update:in";
    public static final String IP6_IN = "ip6:in";
    public static final String IP6_OUT = "ip6:out";
    public static final String DNS_CONFIG = "dns_config";
}
