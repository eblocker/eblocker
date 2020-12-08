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
package org.eblocker.server.common.transaction;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionCache {

    private final int size;

    private int cursor;

    private UUID[] keys;

    private Map<UUID, TransactionContext> cache;

    @Inject
    public TransactionCache(@Named("transaction.cache.size") int size) {
        this.size = size;
    }

    public UUID add(TransactionContext transaction) {
        UUID uuid = UUID.randomUUID();
        synchronized (this) {
            if (cache == null) {
                cursor = 0;
                keys = new UUID[size];
                cache = new ConcurrentHashMap<>();
            }
            cursor++;
            if (cursor == size) cursor = 0;
            if (keys[cursor] != null) {
                cache.remove(keys[cursor]);
            }
            keys[cursor] = uuid;
            cache.put(uuid, new ImmutableTransactionContext(transaction));
        }
        return uuid;
    }

    public TransactionContext get(UUID uuid) {
        synchronized (this) {
            if (cache == null) {
                return null;
            }
            return cache.get(uuid);
        }
    }

}
