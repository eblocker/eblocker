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
package org.eblocker.server.common.network.icmpv6;

import org.eblocker.server.common.data.Ip6Address;

import java.util.List;

public class RouterAdvertisement extends NeighborDiscoveryMessage {

    public static final int ICMP_TYPE = 134;

    public enum RouterPreference {
        RESERVED(-2),
        LOW(-1),
        MEDIUM(0),
        HIGH(1);

        private final int value;

        RouterPreference(int value) {
            this.value = value;
        }

        public static RouterPreference getByValue(int value) {
            if (value < -2 || value > 1) {
                throw new IllegalArgumentException("preference not in range [-2, 1]");
            }
            return values()[value + 2];
        }

        public int getValue() {
            return value;
        }
    }

    private final short currentHopLimit;
    private final boolean managedAddressConfiguration;
    private final boolean otherConfiguration;
    private final boolean homeAgent;
    private final RouterPreference routerPreference;
    private final int routerLifetime;
    private final long reachableTime;
    private final long retransTimer;

    public RouterAdvertisement(byte[] sourceHardwareAddress,
                               Ip6Address sourceAddress,
                               byte[] targetHardwareAddress,
                               Ip6Address targetAddress,
                               short currentHopLimit,
                               boolean managedAddressConfiguration,
                               boolean otherConfiguration,
                               boolean homeAgent,
                               RouterPreference routerPreference,
                               int routerLifetime,
                               long reachableTime,
                               long retransTimer,
                               List<Option> options) {
        super(sourceHardwareAddress, sourceAddress, targetHardwareAddress, targetAddress, ICMP_TYPE, options);
        this.currentHopLimit = currentHopLimit;
        this.managedAddressConfiguration = managedAddressConfiguration;
        this.otherConfiguration = otherConfiguration;
        this.homeAgent = homeAgent;
        this.routerPreference = routerPreference;
        this.routerLifetime = routerLifetime;
        this.reachableTime = reachableTime;
        this.retransTimer = retransTimer;
    }

    public short getCurrentHopLimit() {
        return currentHopLimit;
    }

    public boolean isManagedAddressConfiguration() {
        return managedAddressConfiguration;
    }

    public boolean isOtherConfiguration() {
        return otherConfiguration;
    }

    public boolean isHomeAgent() {
        return homeAgent;
    }

    public RouterPreference getRouterPreference() {
        return routerPreference;
    }

    public int getRouterLifetime() {
        return routerLifetime;
    }

    public long getReachableTime() {
        return reachableTime;
    }

    public long getRetransTimer() {
        return retransTimer;
    }

    @Override
    protected void appendParameter(StringBuilder sb) {
        sb.append("/");
        sb.append(currentHopLimit);
        sb.append("/");
        sb.append(managedAddressConfiguration ? 1 : 0);
        sb.append("/");
        sb.append(otherConfiguration ? 1 : 0);
        sb.append("/");
        sb.append(homeAgent ? 1 : 0);
        sb.append("/");
        sb.append(routerPreference.getValue());
        sb.append("/");
        sb.append(routerLifetime);
        sb.append("/");
        sb.append(reachableTime);
        sb.append("/");
        sb.append(retransTimer);
    }
}
