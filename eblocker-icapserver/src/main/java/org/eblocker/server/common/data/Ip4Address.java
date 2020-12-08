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
package org.eblocker.server.common.data;

public class Ip4Address extends IpAddress {

    private Ip4Address(byte[] address) {
        super(address);
    }

    public static Ip4Address of(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("expected 4 bytes but got " + bytes.length);
        }
        return new Ip4Address(bytes);
    }

    public static Ip4Address parse(String ip) {
        String[] fields = ip.split("\\.");
        if (fields.length != 4) {
            throw new IllegalArgumentException("not an ipv4 address: " + ip);
        }

        byte[] address = new byte[4];
        for (int i = 0; i < 4; ++i) {
            int value = Integer.parseInt(fields[i]);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("not an ipv4 address: " + ip);
            }
            address[i] = (byte) value;
        }

        return new Ip4Address(address);
    }

    @Override
    public boolean isIpv4() {
        return true;
    }

    @Override
    public boolean isIpv6() {
        return false;
    }

    @Override
    protected String generateStringRepresentation() {
        return String.format("%d.%d.%d.%d", address[0] & 0xff, address[1] & 0xff, address[2] & 0xff, address[3] & 0xff);
    }
}
