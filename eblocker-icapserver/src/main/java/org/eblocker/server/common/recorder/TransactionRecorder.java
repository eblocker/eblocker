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
package org.eblocker.server.common.recorder;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.common.network.BaseURLs;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Singleton
public class TransactionRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecorder.class);

    private final ScheduledExecutorService scheduledExecutorService;

    private final DataSource dataSource;

    private final BaseURLs baseURLs;

    private final Object lock = new Object();

    private final long defaultTransactionRecorderTimeLimit;
    private final long defaultTransactionRecorderSizeLimit;
    private final long maxTransactionRecorderTimeLimit;
    private final long maxTransactionRecorderSizeLimit;

    private Date startTime;

    private long gatheredBytes;

    private long maxSeconds;

    private long maxBytes;

    private String deviceId;

    private Set<IpAddress> clientIps;

    private boolean active = false;

    private ScheduledFuture<Void> terminator;

    private AtomicInteger n;

    @Inject
    public TransactionRecorder(
            @Named("highPrioScheduledExecutor") ScheduledExecutorService scheduledExecutorService,
            DataSource dataSource,
            BaseURLs baseURLs,
            @Named("transactionRecorder.default.size") int defaultTransactionRecorderSizeLimit,
            @Named("transactionRecorder.default.time") int defaultTransactionRecorderTimeLimit,
            @Named("transactionRecorder.max.size") int maxTransactionRecorderSizeLimit,
            @Named("transactionRecorder.max.time") int maxTransactionRecorderTimeLimit
    ) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.dataSource = dataSource;
        this.baseURLs = baseURLs;
        this.defaultTransactionRecorderSizeLimit = defaultTransactionRecorderSizeLimit;
        this.defaultTransactionRecorderTimeLimit = defaultTransactionRecorderTimeLimit;
        this.maxTransactionRecorderSizeLimit = maxTransactionRecorderSizeLimit;
        this.maxTransactionRecorderTimeLimit = maxTransactionRecorderTimeLimit;
    }

    public void activate(TransactionRecorderInfo transactionRecorderInfo) {
        deviceId = transactionRecorderInfo.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            throw new BadRequestException("error.device.required");
        }
        Device device = dataSource.getDevice(deviceId);
        if (device == null) {
            LOG.error("Device {} not found", deviceId);
            throw new NotFoundException("error.device.notFound");
        }
        clientIps = new HashSet<>(device.getIpAddresses());

        maxSeconds = defaultTransactionRecorderTimeLimit;
        if (transactionRecorderInfo.getTimeLimitSeconds() > 0) {
            maxSeconds = transactionRecorderInfo.getTimeLimitSeconds();
        }
        if (maxSeconds > maxTransactionRecorderTimeLimit) {
            throw new BadRequestException("error.timeLimit.tooBig");
        }

        maxBytes = defaultTransactionRecorderSizeLimit;
        if (transactionRecorderInfo.getSizeLimitBytes() > 0) {
            maxBytes = transactionRecorderInfo.getSizeLimitBytes();
        }
        if (maxBytes > maxTransactionRecorderSizeLimit) {
            throw new BadRequestException("error.sizeLimit.tooBig");
        }

        synchronized (lock) {
            if (active) {
                throw new ConflictException("error.recorder.active");
            }
            if (terminator != null && !terminator.isDone()) {
                terminator.cancel(true);
            }
            active = true;
            dataSource.deleteAll(RecordedTransaction.class);
            n = new AtomicInteger(0);
            terminator = scheduledExecutorService.schedule(this::deactivate, maxSeconds, TimeUnit.SECONDS);
            startTime = new Date();
            gatheredBytes = 0L;
        }
    }

    public Void deactivate() {
        active = false;
        startTime = null;
        return null;
    }

    public TransactionRecorderInfo getInfo() {
        return new TransactionRecorderInfo(
                deviceId,
                maxSeconds,
                maxBytes,
                active,
                startTime == null ? 0L : (System.currentTimeMillis()-startTime.getTime())/1000L,
                gatheredBytes,
                n == null ? 0 : n.get()
        );
    }

    public void addTransaction(Transaction transaction, boolean outbound) {
        if (!active) {
            return;
        }
        if (!outbound) {
            // Can only process requests at this time.
            // Do not check transaction.isRequest().
            // A blocked or redirected request would be a response now!
            return;
        }
        if (!clientIps.contains(transaction.getOriginalClientIP())) {
            // Wrong client
            return;
        }
        if (transaction.getUrl().startsWith(baseURLs.selectURLForPage(transaction.getUrl()))) {
            // Do not record requests to eBlocker
            return;
        }

        //TODO: Auto stop, if size limit reached

        int id = n.incrementAndGet();
        RecordedTransaction recordedTransaction = new RecordedTransaction(id, transaction);
        dataSource.save(recordedTransaction, id);
    }

    public List<RecordedTransaction> getRecordedTransactions() {
        return dataSource.getAll(RecordedTransaction.class).
                stream().
                sorted(Comparator.comparingInt(RecordedTransaction::getId)).
                collect(Collectors.toList());
    }

    public List<RecordedTransaction> getRecordedTransactions(String domain) {
        return dataSource.getAll(RecordedTransaction.class).
                stream().
                filter(t -> t.getDomain().equals(domain)).
                sorted(Comparator.comparingInt(RecordedTransaction::getId)).
                collect(Collectors.toList());
    }

}
