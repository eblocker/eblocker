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

public class SetupWizardInfo {

    private final boolean setupRequired;
    private final boolean needSerialNumber;
    private final String serialNumberPattern;
    private final String serialNumberExample;
    private boolean registrationAvailable;

    public SetupWizardInfo(boolean setupRequired,
                           boolean needSerialNumber,
                           String serialNumberPattern,
                           String serialNumberExample,
                           boolean registrationAvailable) {
        this.setupRequired = setupRequired;
        this.needSerialNumber = needSerialNumber;
        this.serialNumberPattern = serialNumberPattern;
        this.serialNumberExample = serialNumberExample;
        this.registrationAvailable = registrationAvailable;
    }

    public boolean isSetupRequired() {
        return setupRequired;
    }

    public boolean isNeedSerialNumber() {
        return needSerialNumber;
    }

    public String getSerialNumberPattern() {
        return serialNumberPattern;
    }

    public String getSerialNumberExample() {
        return serialNumberExample;
    }

    public boolean isRegistrationAvailable() {
        return registrationAvailable;
    }
}
