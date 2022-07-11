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
package org.eblocker.server.common.network;

import com.google.common.collect.Table;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.network.icmpv6.Icmp6Message;
import org.eblocker.server.common.network.icmpv6.LinkLayerAddressOption;
import org.eblocker.server.common.network.icmpv6.MtuOption;
import org.eblocker.server.common.network.icmpv6.NeighborAdvertisement;
import org.eblocker.server.common.network.icmpv6.NeighborDiscoveryMessage;
import org.eblocker.server.common.network.icmpv6.NeighborSolicitation;
import org.eblocker.server.common.network.icmpv6.Option;
import org.eblocker.server.common.network.icmpv6.PrefixOption;
import org.eblocker.server.common.network.icmpv6.RecursiveDnsServerOption;
import org.eblocker.server.common.network.icmpv6.RouterAdvertisement;
import org.eblocker.server.common.network.icmpv6.RouterSolicitation;
import org.eblocker.server.common.network.icmpv6.SourceLinkLayerAddressOption;
import org.eblocker.server.common.network.icmpv6.TargetLinkLayerAddressOption;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.service.FeatureToggleRouter;
import org.eblocker.server.common.util.Ip6Utils;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.xml.bind.DatatypeConverter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class NeighborDiscoveryListener implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(NeighborDiscoveryListener.class);

    private final Table<String, IpAddress, Long> arpResponseTable;
    private final Clock clock;
    private final DeviceService deviceService;
    private final FeatureToggleRouter featureToggleRouter;
    private final NetworkInterfaceWrapper networkInterface;
    private final PubSubService pubSubService;
    private final RouterAdvertisementCache routerAdvertisementCache;

    @Inject
    public NeighborDiscoveryListener(@Named("arpResponseTable") Table<String, IpAddress, Long> arpResponseTable,
                                     Clock clock,
                                     DeviceService deviceService,
                                     FeatureToggleRouter featureToggleRouter,
                                     NetworkInterfaceWrapper networkInterface,
                                     PubSubService pubSubService,
                                     RouterAdvertisementCache routerAdvertisementCache) {
        this.arpResponseTable = arpResponseTable;
        this.clock = clock;
        this.deviceService = deviceService;
        this.featureToggleRouter = featureToggleRouter;
        this.networkInterface = networkInterface;
        this.pubSubService = pubSubService;
        this.routerAdvertisementCache = routerAdvertisementCache;
    }

    public void run() {
        pubSubService.subscribeAndLoop("ip6:in", this::subscriber);
    }

    private void subscriber(String message) {
        if (!featureToggleRouter.isIp6Enabled()) {
            return;
        }

        try {
            log.debug("got message: {}", message);
            Icmp6Message parsedMessage = parseMessage(message);
            if (Ip6Address.UNSPECIFIED_ADDRESS.equals(parsedMessage.getSourceAddress())) {
                log.debug("ignoring message with unspecified source address");
                return;
            }

            if (parsedMessage.getIcmpType() == RouterSolicitation.ICMP_TYPE) {
                replyToRouterSolicitation((RouterSolicitation) parsedMessage);
            }

            if (parsedMessage.getIcmpType() == RouterAdvertisement.ICMP_TYPE) {
                routerAdvertisementCache.addEntry((RouterAdvertisement) parsedMessage);
            }

            String deviceId = Device.ID_PREFIX + DatatypeConverter.printHexBinary(getSourceHardwareAddress(parsedMessage)).toLowerCase();
            Device device = deviceService.getDeviceById(deviceId);

            // TODO: refactor to share common code with ArpListener, e.g. create a service
            if (device == null) {
                log.info("ignoring unknown device: {}", deviceId);
                return;
            }

            Ip6Address advertisedAddress = parsedMessage.getSourceAddress();
            if (parsedMessage instanceof NeighborAdvertisement) {
                // In neighbor advertisements the target address is the address that is advertised
                // (see RFC 4861, section 4.4). The source address might be another (e.g. link-local) address.
                advertisedAddress = ((NeighborAdvertisement)parsedMessage).getTargetAddress();
            }

            if (!device.getIpAddresses().contains(advertisedAddress)) {
                log.debug("adding ip address {} for {}", advertisedAddress, device.getId());
                List<IpAddress> ipAddresses = new ArrayList<>(device.getIpAddresses());
                ipAddresses.add(advertisedAddress);
                device.setIpAddresses(ipAddresses);
                deviceService.updateDevice(device);
            }

            log.trace("updated arp response table entry for {}: {}", device.getId(), arpResponseTable.row(device.getHardwareAddress(false)));
            synchronized (arpResponseTable) {
                arpResponseTable.put(device.getHardwareAddress(false), advertisedAddress, clock.millis());
            }
        } catch (MessageException e) {
            log.error("invalid message:", e);
        }
    }

    private void replyToRouterSolicitation(RouterSolicitation solicitation) {
        if (networkInterface.getAddresses().stream()
                .filter(IpAddress::isIpv6)
                .allMatch(ip -> Ip6Utils.isLinkLocal((Ip6Address) ip))) {
            return;
        }

        RouterAdvertisement advertisement = new RouterAdvertisement(
                networkInterface.getHardwareAddress(),
                networkInterface.getIp6LinkLocalAddress(),
                solicitation.getSourceHardwareAddress(),
                solicitation.getSourceAddress(),
                (short) 255,
                false,
                false,
                false,
                RouterAdvertisement.RouterPreference.HIGH,
                120,
                0,
                0,
                Arrays.asList(new RecursiveDnsServerOption(120, Collections.singletonList(networkInterface.getIp6LinkLocalAddress())),
                        new SourceLinkLayerAddressOption(networkInterface.getHardwareAddress()),
                        new MtuOption(networkInterface.getMtu())));
        pubSubService.publish("ip6:out", advertisement.toString());
    }

    private byte[] getSourceHardwareAddress(Icmp6Message message) {
        if (!(message instanceof NeighborDiscoveryMessage)) {
            return message.getSourceHardwareAddress();
        }

        NeighborDiscoveryMessage ndMessage = (NeighborDiscoveryMessage) message;
        return ndMessage.getOptions().stream()
                .filter(o -> o instanceof SourceLinkLayerAddressOption)
                .findFirst()
                .map(o -> (SourceLinkLayerAddressOption) o)
                .map(LinkLayerAddressOption::getHardwareAddress)
                .orElse(message.getSourceHardwareAddress());
    }

    private Icmp6Message parseMessage(String message) throws MessageException {
        MessageReader reader = new MessageReader(message);
        byte[] sourceHardwareAddress = reader.nextHardwareAddress();
        Ip6Address sourceAddress = reader.nextIp6Address();
        byte[] destinationHardwareAddress = reader.nextHardwareAddress();
        Ip6Address destinationAddress = reader.nextIp6Address();
        if (!"icmp6".equals(reader.next())) {
            throw new MessageException("unexpected protocol");
        }
        short icmpType = reader.nextShort();
        switch (icmpType) {
            case RouterSolicitation.ICMP_TYPE:
                return parseRouterSolicitation(reader, sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress);
            case RouterAdvertisement.ICMP_TYPE:
                return parseRouterAdvertisement(reader, sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress);
            case NeighborSolicitation.ICMP_TYPE:
                return parseNeighborSolicitation(reader, sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress);
            case NeighborAdvertisement.ICMP_TYPE:
                return parseNeighborAdvertisement(reader, sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress);
            default:
                return new Icmp6Message(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, icmpType);
        }
    }

    private RouterSolicitation parseRouterSolicitation(MessageReader reader,
                                                       byte[] sourceHardwareAddress,
                                                       Ip6Address sourceAddress,
                                                       byte[] destinationHardwareAddress,
                                                       Ip6Address destinationAddress) throws MessageException {
        List<Option> options = parseOptions(reader);
        return new RouterSolicitation(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, options);
    }

    private RouterAdvertisement parseRouterAdvertisement(MessageReader reader,
                                                         byte[] sourceHardwareAddress,
                                                         Ip6Address sourceAddress,
                                                         byte[] destinationHardwareAddress,
                                                         Ip6Address destinationAddress) throws MessageException {
        short currentHopLimit = reader.nextShort();
        boolean managedAddressConfiguration = reader.nextBoolean();
        boolean otherConfiguration = reader.nextBoolean();
        boolean homeAgent = reader.nextBoolean();
        RouterAdvertisement.RouterPreference routerPreference = RouterAdvertisement.RouterPreference.getByValue(reader.nextInt());
        int routerLifetime = reader.nextInt();
        long reachableTime = reader.nextLong();
        long retransTimer = reader.nextLong();
        List<Option> options = parseOptions(reader);
        return new RouterAdvertisement(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, currentHopLimit, managedAddressConfiguration, otherConfiguration,
                homeAgent, routerPreference, routerLifetime, reachableTime, retransTimer, options);
    }

    private NeighborSolicitation parseNeighborSolicitation(MessageReader reader,
                                                           byte[] sourceHardwareAddress,
                                                           Ip6Address sourceAddress,
                                                           byte[] destinationHardwareAddress,
                                                           Ip6Address destinationAddress) throws MessageException {
        Ip6Address targetAddress = reader.nextIp6Address();
        List<Option> options = parseOptions(reader);
        return new NeighborSolicitation(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, targetAddress, options);
    }

    private NeighborAdvertisement parseNeighborAdvertisement(MessageReader reader,
                                                             byte[] sourceHardwareAddress,
                                                             Ip6Address sourceAddress,
                                                             byte[] destinationHardwareAddress,
                                                             Ip6Address destinationAddress) throws MessageException {
        boolean router = reader.nextBoolean();
        boolean solicited = reader.nextBoolean();
        boolean override = reader.nextBoolean();
        Ip6Address targetAddress = reader.nextIp6Address();
        List<Option> options = parseOptions(reader);
        return new NeighborAdvertisement(sourceHardwareAddress, sourceAddress, destinationHardwareAddress, destinationAddress, router, solicited, override, targetAddress, options);
    }

    private List<Option> parseOptions(MessageReader reader) throws MessageException {
        if (!reader.hasNext()) {
            return Collections.emptyList();
        }

        List<Option> options = new ArrayList<>();
        while (reader.hasNext()) {
            int type = reader.nextInt();
            switch (type) {
                case SourceLinkLayerAddressOption.TYPE:
                    options.add(new SourceLinkLayerAddressOption(reader.nextHardwareAddress()));
                    break;
                case TargetLinkLayerAddressOption.TYPE:
                    options.add(new TargetLinkLayerAddressOption(reader.nextHardwareAddress()));
                    break;
                case PrefixOption.TYPE:
                    options.add(new PrefixOption(reader.nextShort(),
                            reader.nextBoolean(),
                            reader.nextBoolean(),
                            reader.nextLong(),
                            reader.nextLong(),
                            reader.nextIp6Address()));
                    break;
                case MtuOption.TYPE:
                    options.add(new MtuOption(reader.nextLong()));
                    break;
                case RecursiveDnsServerOption.TYPE:
                    long lifetime = reader.nextLong();
                    int dnsServersCount = reader.nextInt();
                    List<Ip6Address> dnsServers = new ArrayList<>(dnsServersCount);
                    for (int i = 0; i < dnsServersCount; ++i) {
                        dnsServers.add(reader.nextIp6Address());
                    }
                    options.add(new RecursiveDnsServerOption(lifetime, dnsServers));
                    break;
                default:
                    throw new MessageException("unknown option: " + type);
            }
        }
        return options;
    }

    private static class MessageException extends Exception {
        MessageException(String message) {
            super(message);
        }

        MessageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class MessageReader {
        private final String[] fields;
        private int index;

        MessageReader(String message) {
            fields = message.split("/");
        }

        boolean hasNext() {
            return index < fields.length;
        }

        String next() throws MessageException {
            if (!hasNext()) {
                throw new MessageException("unexpected end of input");
            }
            return fields[index++];
        }

        short nextShort() throws MessageException {
            return nextNumber(Short::parseShort);
        }

        int nextInt() throws MessageException {
            return nextNumber(Integer::parseInt);
        }

        long nextLong() throws MessageException {
            return nextNumber(Long::parseLong);
        }

        boolean nextBoolean() throws MessageException {
            return nextInt() != 0;
        }

        byte[] nextHardwareAddress() throws MessageException {
            String value = next();
            try {
                byte[] hwAddress = DatatypeConverter.parseHexBinary(value);
                if (hwAddress.length != 6) {
                    throw new MessageException("invalid hardware address: \"" + value + "\"");
                }
                return hwAddress;
            } catch (IllegalArgumentException e) {
                throw new MessageException("invalid hardware address: \"" + value + "\"", e);
            }
        }

        Ip6Address nextIp6Address() throws MessageException {
            String value = next();
            try {
                return Ip6Address.of(DatatypeConverter.parseHexBinary(value));
            } catch (IllegalArgumentException e) {
                throw new MessageException("invalid ip6 address", e);
            }
        }

        private <T> T nextNumber(Function<String, T> parser) throws MessageException {
            String value = next();
            try {
                return parser.apply(value);
            } catch (NumberFormatException e) {
                throw new MessageException("parsing numerical value " + value + " failed ", e);
            }
        }
    }

}
