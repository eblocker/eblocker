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

import java.util.Date;

public class TrafficAccount {
    private Integer id;
    private String mac;
    private Date lastActivity;
    private long packetsIn;
    private long packetsOut;
    private long bytesIn;
    private long bytesOut;

    public TrafficAccount() {
    }

    public TrafficAccount(TrafficAccount that) {
        this.id = that.id;
        this.mac = that.mac;
        this.lastActivity = that.lastActivity;
        this.packetsIn = that.packetsIn;
        this.packetsOut = that.packetsOut;
        this.bytesIn = that.bytesIn;
        this.bytesOut = that.bytesOut;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public Date getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(Date lastActivity) {
        this.lastActivity = lastActivity;
    }

    public long getPacketsIn() {
        return packetsIn;
    }

    public void setPacketsIn(long packetsIn) {
        this.packetsIn = packetsIn;
    }

    public long getPacketsOut() {
        return packetsOut;
    }

    public void setPacketsOut(long packetsOut) {
        this.packetsOut = packetsOut;
    }

    public long getBytesIn() {
        return bytesIn;
    }

    public void setBytesIn(long bytesIn) {
        this.bytesIn = bytesIn;
    }

    public long getBytesOut() {
        return bytesOut;
    }

    public void setBytesOut(long bytesOut) {
        this.bytesOut = bytesOut;
    }

    public void add(TrafficAccount that) {
        packetsIn = this.packetsIn + that.packetsIn;
        packetsOut = this.packetsOut + that.packetsOut;
        bytesIn = this.bytesIn + that.bytesIn;
        bytesOut = this.bytesOut + that.bytesOut;
    }

    public void sub(TrafficAccount that) {
        packetsIn = this.packetsIn - that.packetsIn;
        packetsOut = this.packetsOut - that.packetsOut;
        bytesIn = this.bytesIn - that.bytesIn;
        bytesOut = this.bytesOut - that.bytesOut;
    }

    public static TrafficAccount add(TrafficAccount a, TrafficAccount b) {
        TrafficAccount sum = new TrafficAccount(a);
        sum.add(b);
        return sum;
    }

    public static TrafficAccount sub(TrafficAccount a, TrafficAccount b) {
        TrafficAccount sum = new TrafficAccount(a);
        sum.sub(b);
        return sum;
    }
}
