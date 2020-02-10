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
package org.eblocker.server.common.data.dns;

import org.eblocker.server.common.data.IpAddress;
import com.google.common.base.Strings;

public class DnsResponse {
    private final Integer status;
    private final DnsRecordType recordType;
    private final String name;
    private final IpAddress ipAddress;

    public DnsResponse(String value) {
        if (Strings.isNullOrEmpty(value)) {
            status = null;
            recordType = null;
            name = null;
            ipAddress = null;
        } else {
            String[] values = value.split(",");
            status = Integer.parseInt(values[0]);
            recordType = values.length >= 2 ? DnsRecordType.valueOf(values[1]) : null;
            name = values.length >= 3 ? values[2] : null;
            ipAddress = values.length >= 4 ? IpAddress.parse(values[3]) : null;
        }
    }

    public DnsResponse() {
        this(null, null, null, null);
    }

    public DnsResponse(Integer status) {
        this(status, null, null, null);
    }

    public DnsResponse(Integer status, DnsRecordType recordType, IpAddress ipAddress, String name) {
        this.status = status;
        this.recordType = recordType;
        this.name = name;
        this.ipAddress = ipAddress;
    }

    public DnsRecordType getRecordType() {
        return recordType;
    }

    public Integer getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public IpAddress getIpAddress() {
        return ipAddress;
    }
}
