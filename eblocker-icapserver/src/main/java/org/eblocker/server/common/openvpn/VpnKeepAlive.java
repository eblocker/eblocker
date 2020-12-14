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
package org.eblocker.server.common.openvpn;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VpnKeepAlive {
    private static final Logger log = LoggerFactory.getLogger(VpnKeepAlive.class);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("^PING .* bytes of data.$");
    private static final Pattern REPLY_PATTERN = Pattern.compile("^\\[(\\d+\\.\\d+)] \\d+ bytes from (.*): icmp_seq=(\\d+) ttl=(\\d+) time=(\\d+(\\.\\d+)?) ms$");
    private static final Pattern NO_ANSWER_PATTERN = Pattern.compile("^\\[(\\d+\\.\\d+)] no answer yet for icmp_seq=(\\d+)$");

    private final ScriptRunner scriptRunner;
    private final String interfaceName;
    private final String killCommand;
    private final String pingCommand;
    private final String target;
    private final Executor executor;
    private final Runnable connectionDeadCallback;
    private final int pingInterval;
    private final int noAnswerThreshold;

    private LoggingProcess process;
    private Matcher summaryMatcher;
    private Matcher replyMatcher;
    private Matcher noAnswerMatcher;
    private int consecutiveNoAnswers;
    private boolean callbackCalled;
    private boolean stopped;

    @Inject
    public VpnKeepAlive(@Named("kill.process.command") String killCommand,
                        @Named("ping.process.command") String pingCommand,
                        @Named("vpn.keepalive.ping.interval") int pingInterval,
                        @Named("vpn.keepalive.ping.noAnswerThreshold") int noAnswerThreshold,
                        @Named("unlimitedCachePoolExecutor") Executor executor,
                        ScriptRunner scriptRunner,
                        @Assisted("interfaceName") String interfaceName,
                        @Assisted("target") String target,
                        @Assisted Runnable connectionDeadCallback) {
        this.killCommand = killCommand;
        this.pingCommand = pingCommand;
        this.pingInterval = pingInterval;
        this.noAnswerThreshold = noAnswerThreshold;
        this.executor = executor;
        this.scriptRunner = scriptRunner;
        this.interfaceName = interfaceName;
        this.target = target;
        this.connectionDeadCallback = connectionDeadCallback;
    }

    public void start() {
        executor.execute(() -> {
            try {
                synchronized (VpnKeepAlive.this) {
                    if (process != null) {
                        log.error("already running!");
                        return;
                    }
                    process = scriptRunner.startScript(pingCommand, "-ODi" + pingInterval, "-I" + interfaceName, target);
                }

                summaryMatcher = SUMMARY_PATTERN.matcher("");
                replyMatcher = REPLY_PATTERN.matcher("");
                noAnswerMatcher = NO_ANSWER_PATTERN.matcher("");
                String line;
                while ((line = process.takeStdout()) != null) {
                    handleLogLine(line);
                }
            } catch (IOException e) {
                log.error("reading process output failed", e);
            } catch (InterruptedException e) {
                log.debug("terminating due to interruption");
                Thread.currentThread().interrupt();
            }
        });
    }

    public synchronized void stop() {
        if (process != null && !stopped) {
            try {
                scriptRunner.runScript(killCommand, String.valueOf(process.getPid()));
                stopped = true;
            } catch (IOException e) {
                log.error("failed to run kill script", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleLogLine(String line) {
        summaryMatcher.reset(line);
        if (summaryMatcher.matches()) {
            return;
        }

        replyMatcher.reset(line);
        if (replyMatcher.matches()) {
            handleReply();
            return;
        }

        noAnswerMatcher.reset(line);
        if (noAnswerMatcher.matches()) {
            handleNoAnswer();
            return;
        }

        log.warn("failed to interpret line: {}", line);
    }

    private void handleReply() {
        log.debug("icmp reply from {}: seq={} t={}ms", replyMatcher.group(2), replyMatcher.group(3), replyMatcher.group(5)); //NOSONAR
        consecutiveNoAnswers = 0;
    }

    private void handleNoAnswer() {
        ++consecutiveNoAnswers;
        log.debug("no reply {} / {} from {}: seq={}", consecutiveNoAnswers, noAnswerThreshold, target, noAnswerMatcher.group(2)); //NOSONAR
        if (consecutiveNoAnswers == noAnswerThreshold && !callbackCalled) {
            callbackCalled = true;
            stop();
            log.info("vpn considered down / stalled - restarting");
            connectionDeadCallback.run();
        }
    }

}
