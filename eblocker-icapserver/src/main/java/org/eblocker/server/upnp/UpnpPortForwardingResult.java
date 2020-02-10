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

public class UpnpPortForwardingResult {
    private final UpnpPortForwarding correspondingPortForwarding;
    private boolean success;
    private String errorMsg;

    public UpnpPortForwardingResult(UpnpPortForwarding correspondingPortForwarding) {
        this.correspondingPortForwarding = correspondingPortForwarding;
    }

    public UpnpPortForwardingResult(UpnpPortForwarding correspondingPortForwarding, boolean success, String errorMsg) {
        this.correspondingPortForwarding = correspondingPortForwarding;
        this.success = success;
        this.errorMsg = errorMsg;
    }

    public UpnpPortForwarding getCorrespondingPortForwarding() {
        return correspondingPortForwarding;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
