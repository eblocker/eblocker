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
package org.eblocker.server.common.update;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;

import org.eblocker.server.common.data.UpdatingStatus;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.registration.RegistrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class DebianUpdater implements SystemUpdater {
	private static final Logger log = LoggerFactory.getLogger(DebianUpdater.class);
	private final DeviceRegistrationProperties deviceRegistrationProperties;
	private final ScriptRunner scriptRunner;
	private final String updatesStartCommand;
	private final String updatesRunningCommand;
	private final String updatesDownloadStartCommand;
	private final String updatesDownloadRunningCommand;
	private final String updatesCheckCommand;
	private final String updatesRunningInfoCommand;
	private final DataSource dataSource;
	private LocalDateTime lastUpdate;
	private LoggingProcess updatesCheckRunner;
	private LoggingProcess updatesRunningInfoRunner;
	private ArrayList<String> updateProgress = new ArrayList<>();
	private ArrayList<String> updateablePackages = new ArrayList<>();
    private final String projectVersion;
    private final String pinEblockerListsCommand;
    private final String unpinEblockerListsCommand;
    private final String eBlockerListsPinningFilename;
    private final String eBlockerListsPinningVersion;
    private boolean wasUpdating = false;
    private final UpdateStatusObserver updateStatusObserver;

    @Inject
	public DebianUpdater(DeviceRegistrationProperties deviceRegistrationProperties,
						 ScriptRunner scriptRunner,
						 DataSource dataSource,
                         UpdateStatusObserver updateStatusObserver,
    					 @Named("updates.start.command") String updatesStartCommand,
						 @Named("updates.running.command") String updatesRunningCommand,
	                     @Named("updates.downloadStart.command") String updatesDownloadStartCommand,
	                     @Named("updates.downloadRunning.command") String updatesDownloadRunningCommand,
	                     @Named("updates.check.command") String updatesCheckCommand,
	                     @Named("updates.running.info.command") String updatesRunningInfoCommand,
	                     @Named("pin.eblocker-lists.command") String pinEblockerListsCommand,
	                     @Named("unpin.eblocker-lists.command") String unpinEblockerListsCommand,
	                     @Named("eblocker-lists.pinning.filename") String eBlockerListsPinningFilename,
	                     @Named("eblocker-lists.pinning.version") String eBlockerListsPinningVersion,
	                     @Named("project.version") String projectVersion) {

		this.scriptRunner = scriptRunner;
		this.dataSource=dataSource;
		this.updateStatusObserver = updateStatusObserver;
		this.updatesStartCommand = updatesStartCommand;
		this.updatesRunningCommand = updatesRunningCommand;
		this.updatesDownloadStartCommand = updatesDownloadStartCommand;
		this.updatesDownloadRunningCommand = updatesDownloadRunningCommand;
		this.updatesCheckCommand = updatesCheckCommand;
		this.updatesRunningInfoCommand = updatesRunningInfoCommand;
		this.deviceRegistrationProperties = deviceRegistrationProperties;
		this.lastUpdate = dataSource.getLastUpdateTime();
        this.pinEblockerListsCommand = pinEblockerListsCommand;
        this.unpinEblockerListsCommand = unpinEblockerListsCommand;
        this.eBlockerListsPinningFilename = eBlockerListsPinningFilename;
        this.eBlockerListsPinningVersion = eBlockerListsPinningVersion;
        this.projectVersion = projectVersion;
  	}

	@Override
	public synchronized void startUpdate() throws IOException, InterruptedException, EblockerException {
        if (deviceReady()) {
            log.info("Running update scripts");
			lastUpdate = LocalDateTime.now();
			dataSource.saveLastUpdateTime(lastUpdate);

			updateProgress = new ArrayList<String>();
            updatesRunningInfoRunner = scriptRunner.startScript(updatesRunningInfoCommand);

			scriptRunner.runScript(updatesStartCommand);
			updateStatusObserver.updateStarted(this);
        } else {
            log.info("There are currently updates or downloads running...");
		}
	}

	@Override
	public synchronized State getUpdateStatus() throws IOException, InterruptedException {
		int returnCode = scriptRunner.runScript(updatesRunningCommand);
		if (returnCode == 0) {
		    wasUpdating = true;
			return State.UPDATING;
		}
		else {
		    if (updatesRunningInfoRunner != null) {
		        // Isn't updating anymore, but some progress could be left to save
		        getUpdateProgress();
		        scriptRunner.stopScript(updatesRunningInfoRunner);
		        updatesRunningInfoRunner = null;
		    }
		}
		// Update just stopped, clear list of available updates
		if (wasUpdating) {
		    wasUpdating = false;
		    updateablePackages.clear();
		}

		returnCode = scriptRunner.runScript(updatesDownloadRunningCommand);
		if (returnCode == 0) {
		    return State.DOWNLOADING;
		}

		if (updatesCheckRunner != null && updatesCheckRunner.isAlive()) {
		    return State.CHECKING;
		}

		return State.IDLING;
	}

	@Override
	public LocalDateTime getLastUpdateTime() {
		return lastUpdate;
	}

	@Override
	public void startDownload() throws IOException, InterruptedException {
        if (deviceReady()) {
            log.info("Running download scripts");
            scriptRunner.runScript(updatesDownloadStartCommand);
        } else {
            log.info("There are currently updates or downloads running ...");
	    }
	}

	@Override
	public boolean updatesAvailable() throws IOException, InterruptedException {
	    if (deviceReady()){
            log.info("Running check for updates scripts");
            updateablePackages = new ArrayList<String>();
            updatesCheckRunner = scriptRunner.startScript(updatesCheckCommand);
        } else {
            log.info("There are currently updates or downloads running ...");
        }

        return !updatesAvailablePackages().isEmpty();
	}

	@Override
	public List<String> updatesAvailablePackages() {
	    if (updatesCheckRunner != null && !updatesCheckRunner.isAlive()) {
            // Returning true only if exit value is 0 is more correct in a technical perspective, but as a fallback it might be better to return true when
            // the state is unclear to allow further update steps in this case. Returning a more detailed return value could also be an option here.
            if (updatesCheckRunner.exitValue() != 1) {
                String line = null;
                Scanner scanner = null;

                for (String stdout; (stdout = updatesCheckRunner.pollStdout()) != null;) {
                    scanner = new Scanner(stdout);
                    while (scanner.hasNextLine()) {
                        line = scanner.nextLine();
                        if (line.matches("^Inst.*")) {
                            line = line.replaceAll("^Inst ", "").replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "").trim();
                            log.info("Packages to upgrade: " + line);
                            updateablePackages.add(line);
                        }
                    }
                    scanner.close();
                }
            }
        }

	    return updateablePackages;
	}

	@Override
    public List<String> getUpdateProgress() {
	    if (updatesRunningInfoRunner != null) {
            String line = null;
            Scanner scanner = null;

            for (String stdout; (stdout = updatesRunningInfoRunner.pollStdout()) != null;) {
                scanner = new Scanner(stdout);
                while (scanner.hasNextLine()) {
                    line = scanner.nextLine();

                    if (line.matches("^Get:\\d+ http.*|^Preparing to unpack .*|^Unpacking .*|^Setting up .*")) {
                        log.info("Progress:" + line);
                        updateProgress.add(line);
                    }
                }
                scanner.close();
            }
        }

	    return updateProgress;
	}

	@Override
	public boolean invalidLicense() {
	    RegistrationState state = deviceRegistrationProperties.getRegistrationState();
	    return (RegistrationState.REVOKED == state || RegistrationState.INVALID == state || RegistrationState.OK_UNREGISTERED
            == state);
	}

    @Override
    public boolean pinEblockerListsPackage() throws IOException, InterruptedException {
        try (FileWriter fstream = new FileWriter(Paths.get(System.getProperty("java.io.tmpdir"), eBlockerListsPinningFilename).toString())) {
            BufferedWriter out = new BufferedWriter(fstream);
            out.write("Package: eblocker-lists\n");
            out.write("Pin: version " + eBlockerListsPinningVersion + "~base*\n");
            out.write("Pin-Priority: 999\n\n");
            out.write("Package: eblocker-lists\n");
            out.write("Pin: version " + eBlockerListsPinningVersion + "~daily*\n");
            out.write("Pin-Priority: -1\n");
            out.close();
        }

        return (scriptRunner.runScript(pinEblockerListsCommand) == 0);
    }

    @Override
    public boolean unpinEblockerListsPackage() throws IOException, InterruptedException {
        return (scriptRunner.runScript(unpinEblockerListsCommand) == 0);
    }

    private synchronized boolean deviceReady() throws IOException, InterruptedException {
        deviceRegistrationProperties.makeLicenseCredentialsAvailable();
        deviceRegistrationProperties.acquireRevokationState();

        if (deviceRegistrationProperties.isSubscriptionValid()) {
            if (getUpdateStatus() == State.IDLING) {
                return true;
            }
        } else {
            throw new EblockerException("Running update scripts not possible, because the subscription of this device is not valid or it is not registered.");
        }
        return false;
    }

    public UpdatingStatus getUpdatingStatus() throws IOException, InterruptedException {
        UpdatingStatus status = new UpdatingStatus();
        status.setProjectVersion(projectVersion);
        status.setListsPacketVersion(dataSource.getListsPackageVersion());
        List<String> updateablePackageList = updatesAvailablePackages();
        status.setUpdatesAvailable(!updateablePackageList.isEmpty());

        SystemUpdater.State state = getUpdateStatus();
        status.setUpdating(state == State.UPDATING);
        status.setChecking(state == State.CHECKING);
        status.setDownloading(state == State.DOWNLOADING);

        status.setLastAutomaticUpdate(formatDate(getLastUpdateTime()));

        status.setUpdateProgress(getUpdateProgress());
        status.setUpdateablePackages(updateablePackageList);

        status.setDisabled(invalidLicense());

        return status;
    }

    private String formatDate(LocalDateTime time){
        if(time == null)
            return null;

        ZonedDateTime zdt = time.atZone(TimeZone.getDefault().toZoneId());

        String timestamp = zdt.toString();
        if (timestamp.indexOf("[") > 0) {
            timestamp = timestamp.substring(0, timestamp.indexOf("["));
        }
        return timestamp;
    }

}
