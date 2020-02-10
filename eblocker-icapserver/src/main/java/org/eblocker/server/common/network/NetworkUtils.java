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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.eblocker.server.common.exceptions.EblockerException;

public class NetworkUtils {
    private NetworkUtils() {
    }

	public static String getIPv4NetworkMask(int prefixLength) {
		validatePrefixLength(prefixLength);
		try {
			final int allBits = 0xffffffff; 
			int mask = (allBits >>> prefixLength) ^ allBits;
			byte[] bytes = new byte[4];
			bytes[0] = (byte) ((mask & 0xff000000) >> 24);
			bytes[1] = (byte) ((mask & 0x00ff0000) >> 16);
			bytes[2] = (byte) ((mask & 0x0000ff00) >>  8);
			bytes[3] = (byte)  (mask & 0x000000ff);
			InetAddress result = InetAddress.getByAddress(bytes);
			return result.getHostAddress();
		} catch (UnknownHostException e) { // this should never happen
			throw new EblockerException("Could not convert prefix length " + prefixLength + " to netmask", e);
		}
	}

	public static int getPrefixLength(String ipv4NetworkMask) {
		try {
			InetAddress address = InetAddress.getByName(ipv4NetworkMask);
			byte[] bytes = address.getAddress();
			int mask = 0;
			mask |= (bytes[0] & 0xff) << 24;
			mask |= (bytes[1] & 0xff) << 16;
			mask |= (bytes[2] & 0xff) << 8;
			mask |= (bytes[3] & 0xff);
			int bitmask = 1 << 31;
			int prefixLength = 0;
			boolean endDetected = false; // on the first 0-bit
			for (int i = 1; i <= 32; i++) {
				if ((mask & bitmask) == 0) {
					// bit is not set
					endDetected = true;
				} else {
					// bit is set
					if (endDetected) {
						throw new EblockerException("Could not get prefix length of " + ipv4NetworkMask + ": 1-bit found after 0-bit.");
					}
					prefixLength++;
				}
				bitmask >>>= 1;
			}
			validatePrefixLength(prefixLength);
			return prefixLength;
		} catch (UnknownHostException e) {
			throw new EblockerException("Could not get prefix length of " + ipv4NetworkMask, e);
		}
	}

	public static String getIPv4NetworkAddress(String ipAddress, String networkMask) {
		try {
			InetAddress addr = InetAddress.getByName(ipAddress);
			InetAddress mask = InetAddress.getByName(networkMask);
			byte[] addrBytes = addr.getAddress();
			byte[] maskBytes = mask.getAddress();
			for (int i = 0; i < 4; i++) {
				addrBytes[i] &= maskBytes[i];
			}
			return InetAddress.getByAddress(addrBytes).getHostAddress();
		} catch (UnknownHostException e) {
			throw new EblockerException("Could not get network address of " + ipAddress + " with network mask " + networkMask, e);
		}
	}

	private static void validatePrefixLength(int prefixLength) {
		if (prefixLength < 8 || prefixLength >= 31) {
			throw new EblockerException("Invalid prefixLength: " + prefixLength);
		}
	}
	
	public static String replaceLastByte(String ipv4Address, byte b) {
		try {
			InetAddress gatewayIP = InetAddress.getByName(ipv4Address);
			byte[] bytes = gatewayIP.getAddress();
			if (bytes.length != 4) {
				throw new EblockerException("Expected an IPv4 address");
			}
			bytes[3] = b;
			InetAddress target = InetAddress.getByAddress(bytes);
			return target.getHostAddress();
		} catch (UnknownHostException e) {
			throw new EblockerException("Could not parse IP v4 address", e);
		}
	}
	
	/**Check whether ip1 is before/smaller than ip2 (precondition/required: both IPs are in the same network)
	 * @param ip1
	 * @param ip2
	 * @return
	 */
	public static boolean isBeforeAddress(String ip1,String ip2){
		return (calcLong(ip1)<calcLong(ip2));
	}

	private static long calcLong(String ip) {
		try {
			InetAddress address = InetAddress.getByName(ip);
			byte[] bytes = address.getAddress();
			long mask = 0;
			mask |= (bytes[0] & 0xff) << 24;
			mask |= (bytes[1] & 0xff) << 16;
			mask |= (bytes[2] & 0xff) << 8;
			mask |= (bytes[3] & 0xff);
			return mask;
		}catch (UnknownHostException e) {
			throw new EblockerException("Could not parse IP v4 address", e);
		}
	}
}
