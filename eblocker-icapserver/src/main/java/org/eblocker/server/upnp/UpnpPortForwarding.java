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
package org.eblocker.server.upnp;

import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes;
import org.fourthline.cling.support.model.PortMapping;

public class UpnpPortForwarding extends PortMapping {
    private boolean permanent;// TODO: discuss if this is needed

    public UpnpPortForwarding() {
        super();
    }

    public UpnpPortForwarding(int externalPort, int internalPort, String internalHostIp, int durationSeconds, String description,
                              Protocol protocol, boolean permanent) {
        super();
        this.setExternalPort(new UnsignedIntegerTwoBytes(externalPort));
        this.setInternalPort(new UnsignedIntegerTwoBytes(internalPort));
        this.setInternalClient(internalHostIp);
        this.setLeaseDurationSeconds(new UnsignedIntegerFourBytes(durationSeconds));
        this.setDescription(description);
        this.setProtocol(protocol);
        this.setEnabled(true);
        this.permanent = permanent;
    }

    public UpnpPortForwarding(UpnpPortForwarding orig) {
        this.permanent = orig.isPermament();
        this.setEnabled(orig.isEnabled());
        this.setRemoteHost(orig.getRemoteHost());
        this.setProtocol(orig.getProtocol());
        this.setExternalPort(new UnsignedIntegerTwoBytes(orig.getExternalPort().getValue()));
        this.setInternalPort(new UnsignedIntegerTwoBytes(orig.getInternalPort().getValue()));
        this.setInternalClient(orig.getInternalClient());
        this.setLeaseDurationSeconds(new UnsignedIntegerFourBytes(orig.getLeaseDurationSeconds().getValue()));
        this.setDescription(orig.getDescription());
    }

    public boolean isPermament() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * getExternalPort().hashCode() * getInternalPort().hashCode() * getInternalClient().hashCode()
                * getProtocol().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;

        UpnpPortForwarding other = (UpnpPortForwarding) obj;
        if (!getExternalPort().equals(other.getExternalPort()))
            return false;
        if (!getInternalPort().equals(other.getInternalPort()))
            return false;
        if (!getInternalClient().equals(other.getInternalClient()))
            return false;
        return (getProtocol() == other.getProtocol());
    }

}
