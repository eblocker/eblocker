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
package org.eblocker.server.common.data.validation;

import com.strategicgains.syntaxe.validator.Validator;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NetworkConfigurationValidator implements Validator<NetworkConfiguration> {
    private final Logger log = LoggerFactory.getLogger(NetworkConfigurationValidator.class);
    private static final String ERROR_MESSAGE_PREFIX = "error.network.";
    private static final String ERROR_CODE_IP_ADDRESS_WRONG_NETWORK = "ipAddress.wrongNetwork";

    private void addError(String error, List<String> errors) {
        if (!errors.contains(ERROR_MESSAGE_PREFIX + error)) {//no duplicates of error messages
            errors.add(ERROR_MESSAGE_PREFIX + error);
        }
    }

    @Override
    public void perform(NetworkConfiguration cfg, List<String> errors) {
        if (cfg.isAutomatic()) {
            return; // the easiest case. Ignore all other parameters
        }

        // we need an IP address
        if (cfg.getIpAddress() == null) {
            addError("ipAddress.required", errors);
        } else {
            if (!isValidIPv4Address(cfg.getIpAddress())) {
                addError("ipAddress.invalid", errors);
            }
        }

        // we need a network mask
        if (cfg.getNetworkMask() == null) {
            addError("networkMask.required", errors);
        } else {
            if (!isValidIPv4NetworkMask(cfg.getNetworkMask())) {
                addError("networkMask.invalid", errors);
            }
        }

        // we need a gateway
        if (cfg.getGateway() == null) {
            addError("gateway.required", errors);
        } else {
            if (!isValidIPv4Address(cfg.getGateway())) {
                addError("gateway.invalid", errors);
            }
        }

        // optional fields:
        validateOptionalIPAddress(cfg.getNameServerPrimary(), "nameServerPrimary", errors);
        validateOptionalIPAddress(cfg.getNameServerSecondary(), "nameServerSecondary", errors);
        validateOptionalIPv4Address(cfg.getDhcpRangeFirst(), "dhcpRangeFirst", errors);
        validateOptionalIPv4Address(cfg.getDhcpRangeLast(), "dhcpRangeLast", errors);

        //IMPORTANT: check if addresses are from the same network
        validateSameNetwork(cfg, errors);

        //check if the DHCP range is valid
        validateDHCPRange(cfg, errors);

    }

    private void validateDHCPRange(NetworkConfiguration cfg, List<String> errors) {
        if (cfg.isDhcp()) {
            //make sure the range first ip address is smaller than the range last ip address
            String rangeFirst = cfg.getDhcpRangeFirst();
            String rangeLast = cfg.getDhcpRangeLast();
            String networkMask = cfg.getNetworkMask();

            if (rangeFirst != null && rangeLast != null && networkMask != null) {
                if (!isSameNetwork(rangeFirst, rangeLast, networkMask)) {//make sure that dhcpRangeFirst and dhcpRangeLast are in the same network
                    addError("dhcp.wrongNetwork", errors);
                    return;
                }

                if (!NetworkUtils.isBeforeAddress(rangeFirst, rangeLast)) {//make sure that dhcpRangeFirst is logically before dhcpRangeLast
                    addError("dhcp.invalidRange", errors);
                }
            }
        }
    }

    /**
     * Checks whether the addresses are from the same network that the IP address and the network mask define.
     * If there is a invalid setting then add error message (formatted like "error.network.[variable].wrongNetwork") to the exception which is thrown
     *
     * @param cfg
     * @param errors
     */
    private void validateSameNetwork(NetworkConfiguration cfg, List<String> errors) {
        if (cfg.getIpAddress() != null && cfg.getNetworkMask() != null) {//only if ipAddress and network mask are set (for comparison)

            //gateway should be in the same network as the IP address
            if (cfg.getGateway() != null && !isSameNetwork(cfg.getIpAddress(), cfg.getGateway(), cfg.getNetworkMask())) {
                addError(ERROR_CODE_IP_ADDRESS_WRONG_NETWORK, errors);
                addError("gateway.wrongNetwork", errors);
            }

            // If the DHCP server is active, make sure that the network of the
            // DHCP range is also fitting the ipAddress network
            if (cfg.isDhcp()) {
                if (cfg.getDhcpRangeFirst() != null && !isSameNetwork(cfg.getIpAddress(), cfg.getDhcpRangeFirst(), cfg.getNetworkMask())) {
                    addError(ERROR_CODE_IP_ADDRESS_WRONG_NETWORK, errors);
                    addError("dhcpRangeFirst.wrongNetwork", errors);
                }
                if (cfg.getDhcpRangeLast() != null && !isSameNetwork(cfg.getIpAddress(), cfg.getDhcpRangeLast(), cfg.getNetworkMask())) {
                    addError(ERROR_CODE_IP_ADDRESS_WRONG_NETWORK, errors);
                    addError("dhcpRangeLast.wrongNetwork", errors);
                }
            }
        }
    }

    /**
     * Check if the ipAddress and the otherAddress are on the same network
     *
     * @param ipAddress    eBlockers IP
     * @param otherAddress another address e.g. the gateway address
     * @param networkMask
     * @return
     */
    private boolean isSameNetwork(String ipAddress, String otherAddress, String networkMask) {
        if (isValidIPv4Address(ipAddress) && isValidIPv4Address(otherAddress) && isValidIPv4Address(networkMask)) {
            String networkAddress1 = NetworkUtils.getIPv4NetworkAddress(ipAddress, networkMask);
            String networkAddress2 = NetworkUtils.getIPv4NetworkAddress(otherAddress, networkMask);

            return networkAddress1.equals(networkAddress2);
        }
        return false;
    }

    private void validateOptionalIPv4Address(String address, String name, List<String> errors) {
        if (address == null) {
            return;
        }
        if (!isValidIPv4Address(address)) {
            addError(name + ".invalid", errors);
        }
    }

    private void validateOptionalIPAddress(String address, String name, List<String> errors) {
        if (address == null) {
            return;
        }
        if (!isValidIPAddress(address)) {
            addError(name + ".invalid", errors);
        }
    }

    private boolean isValidIPv4NetworkMask(String networkMask) {
        if (!isValidIPv4Address(networkMask)) {
            return false;
        }

        try {
            NetworkUtils.getPrefixLength(networkMask);
        } catch (Exception e) {
            log.info(String.format("Network mask %s invalid", networkMask), e);
            return false;
        }

        return true;
    }

    public static boolean isValidIPv4Address(String ipAddress) {
        try {
            Ip4Address.parse(ipAddress);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidIPAddress(String ipAddress) {
        try {
            IpAddress.parse(ipAddress);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
