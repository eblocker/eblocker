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
package org.eblocker.server.common.session;

import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.transaction.TransactionIdentifier;

public class SessionIdentifier implements TransactionIdentifier {

    private String userAgent;
    private IpAddress IP;

    public SessionIdentifier(IpAddress ip, String userAgent) {
        this.IP = ip;
        this.userAgent = userAgent;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public IpAddress getOriginalClientIP() {
        return IP;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SessionIdentifier other = (SessionIdentifier) obj;
        if (IP == null) {
            if (other.IP != null)
                return false;
        } else if (!IP.equals(other.IP))
            return false;
        if (userAgent == null) {
            if (other.userAgent != null)
                return false;
        } else if (!userAgent.equals(other.userAgent))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((IP == null) ? 0 : IP.hashCode());
        result = prime * result + ((userAgent == null) ? 0 : userAgent.hashCode());
        return result;
    }


    @Override
    public String toString() {
        return "IP: " + IP + " userAgent: " + userAgent;
    }

}
