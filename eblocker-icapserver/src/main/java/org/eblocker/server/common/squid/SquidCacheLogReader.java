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
package org.eblocker.server.common.squid;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.util.TailReader;
import org.eblocker.server.http.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class SquidCacheLogReader {
    private static final Logger log = LoggerFactory.getLogger(SquidCacheLogReader.class);

    private static final Pattern FAILED_CONNECTION_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}).*\\| eblkr: (.*) log_addr: (.*):\\d+ host: (.*) sni: (.*) cert: (.*)$");
    private static final String NULL = "<null>";

    private final String squidCacheLog;
    private final long sleep;
    private final DeviceService deviceService;
    private final Executor executor;

    private final Queue<FailedConnectionLogEntry> failedConnectionLogEntries;

    private TailReader tailReader;
    private BufferedReader squidLogReader;

    @Inject
    public SquidCacheLogReader(@Named("squid.cache.log") String squidCacheLog,
                               @Named("squid.cache.log.reader.sleep") long sleep,
                               DeviceService deviceService,
                               @Named("unlimitedCachePoolExecutor") Executor executor) throws IOException {
        this.squidCacheLog = squidCacheLog;
        this.sleep = sleep;
        this.deviceService = deviceService;
        this.executor = executor;

        this.failedConnectionLogEntries = new ConcurrentLinkedQueue<>();
    }

    public synchronized void start() throws IOException {
        if (squidLogReader != null) {
            log.warn("cache log reader already started!");
            return;
        }

        log.debug("starting squid cache log reader");
        tailReader = new TailReader(squidCacheLog, true, sleep);
        squidLogReader = new BufferedReader(tailReader);
        executor.execute(this::readSquidLog);
    }

    public synchronized void stop() throws IOException {
        if (squidLogReader == null) {
            log.warn("log reader already stopped!");
            return;
        }

        log.debug("stopping squid cache log reader");
        tailReader.close();
        squidLogReader.close();
        squidLogReader = null;
        log.debug("squid cache log reader stopped");
    }

    public List<FailedConnectionLogEntry> pollFailedConnections() {
        List<FailedConnectionLogEntry> entries = new ArrayList<>();
        FailedConnectionLogEntry entry;
        while ((entry = failedConnectionLogEntries.poll()) != null) {
            entries.add(entry);
        }
        return entries;
    }

    private void readSquidLog() {
        try {
            String line;
            while ((line = squidLogReader.readLine()) != null) {
                log.debug("read cache log line: {}", line);
                FailedConnectionLogEntry connection = parseLine(line);
                if (connection != null) {
                    log.debug("adding failed connection to {} by device {}", connection.getSni(), connection.getDeviceId());
                    failedConnectionLogEntries.add(connection);
                }
            }
        } catch (IOException e) {
            log.error("parsing squid cache log failed", e);
        }
        log.info("reading log finished");
    }

    private FailedConnectionLogEntry parseLine(String line) {
        Matcher matcher = FAILED_CONNECTION_PATTERN.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int dayOfMonth = Integer.parseInt(matcher.group(3));
        int hour = Integer.parseInt(matcher.group(4));
        int minute = Integer.parseInt(matcher.group(5));
        int second = Integer.parseInt(matcher.group(6));
        ZonedDateTime dt = ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, 0, ZoneId.systemDefault());

        String error = matcher.group(7);

        IpAddress ip = IpAddress.parse(matcher.group(8));
        Device device = deviceService.getDeviceByIp(ip);
        if (device == null) {
            log.warn("no device known for {}", ip);
            return null;
        }

        String host = parseHost(matcher.group(9));
        String sni = parseHost(matcher.group(10));
        String certificate = parseCertificate(matcher.group(11));

        return new FailedConnectionLogEntry(dt.toInstant(), error, device.getId(), host, sni, certificate);
    }

    private String parseHost(String value) {
        return NULL.equals(value) || "".equals(value) ? null : value;
    }

    private String parseCertificate(String value) {
        if (NULL.equals(value) || "".equals(value)) {
            return null;
        }
        return value.replaceAll("\\\\n", "\n");
    }

}
