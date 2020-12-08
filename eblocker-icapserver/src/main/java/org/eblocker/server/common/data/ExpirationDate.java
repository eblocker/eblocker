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

public class ExpirationDate {
    private final Date expirationDate;
    private final long daysTillExpiration;

    public ExpirationDate(Date expirationDate, Long daysTillExpiration2) {
        this.expirationDate = expirationDate;
        this.daysTillExpiration = daysTillExpiration2;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public long getDaysTillExpiration() {
        return daysTillExpiration;
    }
}
