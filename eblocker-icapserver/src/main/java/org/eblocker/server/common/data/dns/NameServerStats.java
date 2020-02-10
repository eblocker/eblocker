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

public class NameServerStats {
    private final String nameServer;
    private final int valid;
    private final int invalid;
    private final int error;
    private final int timeout;
    private final long responseTimeAverage;
    private final long responseTimeMedian;
    private final long responseTimeMin;
    private final long responseTimeMax;
    private final DnsRating rating;
    private final DnsReliabilityRating reliabilityRating;
    private final DnsResponseTimeRating responseTimeRating;

    public NameServerStats(String nameServer, int valid, int invalid, int error, int timeout, long responseTimeAverage, long responseTimeMedian, long responseTimeMin, long responseTimeMax,
                           DnsRating rating, DnsReliabilityRating reliabilityRating, DnsResponseTimeRating responseTimeRating) {
        this.nameServer = nameServer;
        this.valid = valid;
        this.invalid = invalid;
        this.error = error;
        this.timeout = timeout;
        this.responseTimeAverage = responseTimeAverage;
        this.responseTimeMedian = responseTimeMedian;
        this.responseTimeMin = responseTimeMin;
        this.responseTimeMax = responseTimeMax;
        this.rating = rating;
        this.reliabilityRating = reliabilityRating;
        this.responseTimeRating = responseTimeRating;
    }

    public String getNameServer() {
        return nameServer;
    }

    public int getValid() {
        return valid;
    }

    public int getInvalid() {
        return invalid;
    }

    public int getError() {
        return error;
    }

    public int getTimeout() {
        return timeout;
    }

    public long getResponseTimeAverage() {
        return responseTimeAverage;
    }

    public long getResponseTimeMedian() {
        return responseTimeMedian;
    }

    public long getResponseTimeMin() {
        return responseTimeMin;
    }

    public long getResponseTimeMax() {
        return responseTimeMax;
    }

    public DnsRating getRating() {
        return rating;
    }

    public DnsReliabilityRating getReliabilityRating() {
        return reliabilityRating;
    }

    public DnsResponseTimeRating getResponseTimeRating() {
        return responseTimeRating;
    }
}
