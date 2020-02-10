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

/**
 * Contains data for an ARP packet.
 *
 * There is a string representation that can be parsed. It is defined in: network-tools/arp_write.c.
 */
public class ArpMessage {
	public ArpMessageType type;
	public String sourceHardwareAddress;
	public String sourceIPAddress;
	public String targetHardwareAddress;
	public String targetIPAddress;
	public String ethernetTargetHardwareAddress;

	private static final String separator = "/";

	/**
	 * Parse the string representation. See also: network-tools/arp_write.c
	 * @param message
	 * @return
	 * @throws ArpMessageParsingException
	 */
	public static ArpMessage parse(String message) throws ArpMessageParsingException {
		String[] parts = message.split(separator);
		if (parts.length != 5) {
			throw new ArpMessageParsingException("Expected 5 elements, got: " + parts.length);
		}

		ArpMessage result = new ArpMessage();
		try {
			int code = Integer.parseInt(parts[0]);
			if (ArpMessageType.isRequest(code)) {
				result.type = ArpMessageType.ARP_REQUEST;
			} else if (ArpMessageType.isResponse(code)) {
				result.type = ArpMessageType.ARP_RESPONSE;
			} else {
				throw new ArpMessageParsingException("Only codes 1 (ARP request) and 2 (ARP response) are supported");
			}
		} catch (Exception e) {
			throw new ArpMessageParsingException("Could not parse operation code", e);
		}

		result.sourceHardwareAddress = parts[1];
		result.sourceIPAddress       = parts[2];
		result.targetHardwareAddress = parts[3];
		result.targetIPAddress       = parts[4];

		return result;
	}

	/**
	 * Creates the string representation
	 * @return
	 */
	public String format() {
		StringBuilder sb = new StringBuilder();

		sb.append(type.getCode());        sb.append(separator);
		sb.append(sourceHardwareAddress); sb.append(separator);
		sb.append(sourceIPAddress);       sb.append(separator);
		sb.append(targetHardwareAddress); sb.append(separator);
		sb.append(targetIPAddress);
		if (ethernetTargetHardwareAddress != null) {
			sb.append(separator);
			sb.append(ethernetTargetHardwareAddress);
		}

		return sb.toString();
	}

	public String toString() {
		return "ArpMessage(" + format() + ")";
	}

	/**
	 * A gratuitous request has the same source and target IP address
	 * @return
	 */
    public boolean isGratuitousRequest() {
        return type == ArpMessageType.ARP_REQUEST &&
               sourceIPAddress != null &&
               sourceIPAddress.equals(targetIPAddress) &&
               !"0.0.0.0".equals(sourceIPAddress);
    }

    public boolean isArpProbe() {
        return type == ArpMessageType.ARP_REQUEST &&
               sourceHardwareAddress != null &&
               "0.0.0.0".equals(sourceIPAddress) &&
               "000000000000".equals(targetHardwareAddress) &&
               targetIPAddress != null &&
               !"0.0.0.0".equals(targetIPAddress);
    }
}
