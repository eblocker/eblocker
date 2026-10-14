/*
 * Copyright 2025 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.system;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.startup.SubSystemService;

/**
 * Checks system requirements, e.g. is there enough RAM to run the eBlocker system?
 */
@Singleton
@SubSystemService(SubSystem.SYSTEM_CHECK)
public class SystemRequirements {
    private final int requiredHeapSizeMb;
    private final String requiredTotalMemory;

    @Inject
    public SystemRequirements(@Named("system.requirement.heap_size_mb") int requiredHeapSizeMb,
                              @Named("system.requirement.total_memory") String requiredTotalMemory) {
        this.requiredHeapSizeMb = requiredHeapSizeMb;
        this.requiredTotalMemory = requiredTotalMemory;
    }

    public void check() {
        long heapSizeMb = Runtime.getRuntime().maxMemory() / 1048576L;
        if (heapSizeMb < requiredHeapSizeMb) {
            throw new EblockerException(requiredTotalMemory + " RAM required.");
        }
    }
}
