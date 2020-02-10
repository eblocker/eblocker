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

import java.time.Duration;

public class UsageAccount {
    private boolean active;
    private boolean allowed;
    private Duration usedTime;
    private Duration accountedTime;
    private Duration maxUsageTime;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public Duration getAccountedTime() {
        return accountedTime;
    }

    public void setAccountedTime(Duration accountedTime) {
        this.accountedTime = accountedTime;
    }

    public Duration getUsedTime() {
        return usedTime;
    }

    public void setUsedTime(Duration usedTime) {
        this.usedTime = usedTime;
    }

    public Duration getMaxUsageTime() {
        return maxUsageTime;
    }

    public void setMaxUsageTime(Duration maxUsageTime) {
        this.maxUsageTime = maxUsageTime;
    }
}
