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
package org.eblocker.server.common.blacklist;

import org.eblocker.server.common.data.parentalcontrol.BlockedDomainLogEntry;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.statistic.BlockedDomainsStatisticService;
import org.eblocker.server.common.executor.NamedRunnable;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;

@Singleton
public class BlockedDomainLog {

    private final BlockingQueue<BlockedDomainLogEntry> entries = new LinkedBlockingDeque<>();

    @Inject
    public BlockedDomainLog(BlockedDomainsStatisticService statisticService,
                            @Named("unlimitedCachePoolExecutor") Executor executor) {
        executor.execute(new NamedRunnable("BlockedDomainLog", () -> {
            try {
                while(true) {
                    statisticService.countBlockedDomain(entries.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    public void addEntry(String deviceId, String domain, Integer listId) {
        entries.add(new BlockedDomainLogEntry(deviceId, domain, listId));
    }

    public void addEntry(String deviceId, String domain, Category category) {
        entries.add(new BlockedDomainLogEntry(deviceId,domain, category));
    }
}
