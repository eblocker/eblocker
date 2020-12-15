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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.RecordedSSLAppData;
import org.eblocker.server.common.data.RecordedSSLHandshake;
import org.eblocker.server.common.data.RecordedUrl;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.StartRecordingRequestData;
import org.eblocker.server.common.util.TextLineProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordingService {
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
    DataSource dataSource;
    ScriptRunner scriptRunner;
    AppModuleService appModuleService;
    TextLineProvider lineProvider;
    Clock clock;
    /*
     * These variables are to monitor the recording and stop it after a certain
     * limit has been reached - either the time or the file containing the
     * recorded data has grown too big.
     */
    private Instant recordingStartTime;
    private int recordingTimeLimit;
    private int recordingSizeLimit;
    private int defaultRecordingTimeLimit;
    private int defaultRecordingSizeLimit;
    private String recordingFilePath;
    private String analysisFileHandshakesPath;
    private String analysisFileDataPath;

    @Inject
    public RecordingService(
            @Named("recording.default.size") int defaultRecordingSizeLimit,
            @Named("recording.default.time") int defaultRecordingTimeLimit,
            @Named("recording.recording.file.path") String recordingFilePath,
            @Named("recording.analysis.handshakes.file.path") String analysisFileHandshakesPath,
            @Named("recording.analysis.data.file.path") String analysisFileDataPath, DataSource dataSource,
            ScriptRunner scriptRunner, AppModuleService appModuleService, TextLineProvider textLineProvider,
            Clock clock) {
        this.defaultRecordingSizeLimit = defaultRecordingSizeLimit;
        this.defaultRecordingTimeLimit = defaultRecordingTimeLimit;
        this.recordingFilePath = recordingFilePath;
        this.analysisFileHandshakesPath = analysisFileHandshakesPath;
        this.analysisFileDataPath = analysisFileDataPath;
        this.dataSource = dataSource;
        this.scriptRunner = scriptRunner;
        this.appModuleService = appModuleService;
        this.lineProvider = textLineProvider;
        this.clock = clock;
    }

    public boolean recordingStartStop(StartRecordingRequestData requestData) {
        /*
         * map contains: recordingStatus: either "start" or "stop" targetID: the
         * device id (used to get the ip) timeLimit: integer for the minutes
         * sizeLimit: integer for the megabytes
         * userWhitelistSetting: Dictionary with (url, status)-pairs where
         * status is one of "block", "bump" or "whitelist"
         */
        if (requestData.getRecordingStatus().equals("start")) {

            if (this.recordingStartTime != null) {
                // recording is already running, cannot start again
                return false;
            }
            // get ip address from device id
            Device device;
            try {
                device = dataSource.getDevice(requestData.getTargetID());
                if (device == null) {
                    log.info("recordingStartStop: unrecognized deviceID");
                    return false;
                }
            } catch (Exception e) {
                log.debug("recordingStartStop: targetID could not be processed.", e);
                return false;
            }
            String deviceIP = device.getIpAddresses().get(0).toString(); // TODO: [EB1-1077] how to handle multiple addresses here? Record all traffic ?
            // check for meaningful limits
            if (requestData.getTimeLimit() > 0 && requestData.getSizeLimit() > 0) {
                log.info("Start-stop: starting");
                // limits
                this.recordingTimeLimit = this.defaultRecordingTimeLimit;
                this.recordingSizeLimit = this.defaultRecordingSizeLimit;
                this.recordingTimeLimit = requestData.getTimeLimit();
                this.recordingSizeLimit = requestData.getSizeLimit();
                // start the actual recording
                try {
                    this.scriptRunner.runScript("recording_start", deviceIP);
                    this.recordingStartTime = this.clock.instant();

                } catch (IOException e) {
                    log.error("recordingStartStop: start.", e);
                    return false;
                } catch (InterruptedException e) {
                    log.error("recordingStartStop: start - interrupted", e);
                    Thread.currentThread().interrupt();
                    return false;
                }

                return true;
            } else {
                log.info("Start-stop: Negative limits not allowed");
                return false;
            }
        } else if (requestData.getRecordingStatus().equals("stop")) {
            log.info("Start-stop: stopping");

            try {
                this.scriptRunner.runScript("recording_stop", "");
                this.recordingStartTime = null;
                this.recordingSizeLimit = 0;
                this.recordingTimeLimit = 0;
            } catch (IOException e) {
                log.error("recordingStartStop: stop.", e);
            } catch (InterruptedException e) {
                log.error("recordingStartStop: stop. - interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                // in any case, restore regular bumping/whitelisting
                appModuleService.storeAndActivateEnabledState(appModuleService.getTempAppModuleId(), false);
            }
            return true;
        } else {
            log.info("Start-stop: unrecognized parameter");
        }
        return false;
    }

    public boolean getRecordingStatus() {
        try {
            int retVal = this.scriptRunner.runScript("recording_status");

            boolean running = (retVal == 0);

            // Repair recordingStartTime - it should not be null of course, if
            // the process is running
            if (running && recordingStartTime == null) {
                recordingStartTime = this.clock.instant();
            }

            // If it's not running, we are ready now.
            if (!running) {
                recordingStartTime = null;
                return false;
            }

            // check if the recording must be stopped either due to time limit
            boolean overTime = this.clock.instant()
                    .isAfter(this.recordingStartTime.plusSeconds(this.recordingTimeLimit));
            // or size limit
            File recordingFile = new File(this.recordingFilePath);
            long recordingFileSize = recordingFile.length() / (1024 * 1024);
            if (overTime || recordingFileSize >= this.recordingSizeLimit) {
                // stop recording
                try {
                    log.info("getRecordingStatus: stop: time/size limit reached");
                    this.scriptRunner.runScript("recording_stop", "");
                    this.recordingStartTime = null;
                    this.recordingSizeLimit = 0;
                    this.recordingTimeLimit = 0;
                } catch (IOException e) {
                    log.error("recordingStartStop: stop (size/time).", e);
                } catch (InterruptedException e) {
                    log.error("recordingStartStop: stop (size/time) - interrupted.", e);
                    Thread.currentThread().interrupt();
                } finally {
                    // in any case, restore regular bumping/whitelisting
                    appModuleService.storeAndActivateEnabledState(appModuleService.getTempAppModuleId(), false);
                }
                // since it is stopped, return proper status
                return false;
            }
            return true;

        } catch (IOException e) {
            log.debug("getRecordingStatus.", e);
        } catch (InterruptedException e) {
            log.debug("getRecordingStatus.", e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private void recordingStartAnalysis() {
        log.info("StartAnalysis start");

        try {
            this.scriptRunner.runScript("processing-script.sh");
        } catch (IOException e) {
            log.error("recordingStartAnalysis: {}", e);
        } catch (InterruptedException e) {
            log.error("recordingStartAnalysis interrupted", e);
            Thread.currentThread().interrupt();
        }
        log.info("StartAnalysis stop");
    }

    public List<RecordedUrl> getRecordedDomainList() {
        // start the analysis
        this.recordingStartAnalysis();

        Map<Integer, RecordedSSLHandshake> recordedHandshakes = new HashMap<>();
        // read recorded handshakes from file
        for (String line : this.lineProvider.getLines(this.analysisFileHandshakesPath)) {
            RecordedSSLHandshake handshake = RecordedSSLHandshake.parse(line);
            // if the handshake is already in the list, do nothing.
            if (handshake == null || recordedHandshakes.containsKey(handshake.getTCPStreamNumber())) {
                // nothing
            } else {
                recordedHandshakes.put(handshake.getTCPStreamNumber(), handshake);
            }
        }

        // read recorded app data from file
        List<RecordedSSLAppData> recordedAppData = new ArrayList<>();
        // read recorded app data from file
        for (String line : this.lineProvider.getLines(this.analysisFileDataPath)) {
            RecordedSSLAppData appData = RecordedSSLAppData.parse(line);
            if (appData != null) {
                recordedAppData.add(appData);
            }
        }

        // update all SSL handshakes with the corresponding app data (if such
        // exists)
        for (RecordedSSLAppData appData : recordedAppData) {
            if (recordedHandshakes.containsKey(appData.getTCPStreamNumber())) {
                recordedHandshakes.get(appData.getTCPStreamNumber()).updateWithAppData(appData);
            } else {
                // probably a connection that started before the recording did
            }
        }

        // till now, there is a list according to tcp streams. turn it into a
        // list according
        // to destination domain/ip
        Map<String, RecordedUrl> recordedUrls = new HashMap<>();
        for (Integer handshakeTCPStream : recordedHandshakes.keySet()) {
            // either the recorded url to be inserted or the one to be updated
            RecordedUrl tmpRecordedUrl;
            // the handshake that is used
            RecordedSSLHandshake tmpHandshake = recordedHandshakes.get(handshakeTCPStream);

            // if url corresponding to this stream is not in the result list
            if (!recordedUrls.containsKey(tmpHandshake.getServername())) {
                tmpRecordedUrl = new RecordedUrl(tmpHandshake);
                recordedUrls.put(tmpHandshake.getServername(), tmpRecordedUrl);
            } else {
                tmpRecordedUrl = recordedUrls.get(tmpHandshake.getServername());
            }

            // now about the recommendation (there may already be a stream with
            // observed app data present but the stream currently handled has no
            // corresponding app data)
            tmpRecordedUrl.adjustWhitelistRecommendation(tmpHandshake.isCorrespondingAppDataRecorded());
        }

        // copy contents from the dictionary into the result
        List<RecordedUrl> result = new ArrayList<>();
        for (String url : recordedUrls.keySet()) {
            RecordedUrl tmpRecUrl = recordedUrls.get(url);
            // if the url is already whitelisted, tell the user
            result.add(tmpRecUrl);
        }

        return result;
    }

}
