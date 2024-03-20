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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.system.LoggingProcess;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DebianUpdaterTest {
    private final String updatesStartCommand = "updatesStartCommand";
    private final String updatesRunningCommand = "updatesRunningCommand";
    private final String updatesDownloadStartCommand = "updatesDownloadStartCommand";
    private final String updatesDownloadRunningCommand = "updatesDownloadRunningCommand";
    private final String updatesCheckCommand = "updatesCheckCommand";
    private final String updatesRunningInfoCommand = "updatesRunningInfoCommand";
    private final String updatesFailedCommand = "updatesFailedCommand";
    private final String updatesRecoveryStartCommand = "updatesRecoveryStartCommand";
    private final String updatesRecoveryRunningCommand = "updatesRecoveryRunningCommand";
    private final LocalDateTime lastUpdateTime = LocalDateTime.of(2017, 01, 01, 12, 00);
    private final String availableUpdatesString1 = "Inst libtiff5 [4.0.6-1] (4.0.6-1ubuntu0.1 Ubuntu:16.04/xenial-updates, Ubuntu:16.04/xenial-security [amd64])\n"
            + "Conf libtiff5 (4.0.6-1ubuntu0.1 Ubuntu:16.04/xenial-updates, Ubuntu:16.04/xenial-security [amd64])\n";
    private final String availableUpdatesString2 = "Inst kpartx [0.5.0+git1.656f8865-5ubuntu2.3] (0.5.0+git1.656f8865-5ubuntu2.4 Ubuntu:16.04/xenial-updates [amd64])\n"
            + "Conf kpartx (0.5.0+git1.656f8865-5ubuntu2.4 Ubuntu:16.04/xenial-updates [amd64])"
            + "foo bar";
    private final String updateProgressString1 = "Get:1 https://apt.stage.eblocker.com/eblocker-lists buster/main armhf eblocker-lists all 2.5.3~daily+20201103071505 [38.2 MB]\n"
            + "Preparing to unpack .../eblocker-lists_1.0.0~20170222084502_all.deb ...\n"
            + "Unpacking eblocker-lists (1.0.0~20170222084502) over (1.0.0~20170221084503) ...\n"
            + "Setting up eblocker-lists (1.0.0~20170222084502) ..\n";
    private final String updateProgressString2 = "insserv: script tor.distrib: service tor already provided!\n"
            + "insserv: script tor.site: service tor already provided!\n"
            + "insserv: script redsocks.distrib: service redsocks already provided!";
    private final String projectVersion = "1.8.4";
    private final String pinEblockerListsCommand = "pin-eblocker-lists";
    private final String unpinEblockerListsCommand = "unpin-eblocker-lists";
    private final String eBlockerListsPinningFilename = "01-eblocker-lists";
    private final String eBlockerListsPinningVersion = "1.8.0";
    private ScriptRunner scriptRunner;
    private DeviceRegistrationProperties deviceRegistrationProperties;
    private DataSource datasource;
    private static final String TEST_PATH = "test-data";
    private UpdateStatusObserver updateStatusObserver;

    @Before
    public void setUp() throws Exception {
        scriptRunner = Mockito.mock(ScriptRunner.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        datasource = Mockito.mock(DataSource.class);
        updateStatusObserver = Mockito.mock(UpdateStatusObserver.class);
        Mockito.when(datasource.getLastUpdateTime()).thenReturn(lastUpdateTime);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    DebianUpdater createDebianUpdate() throws IOException, InterruptedException {
        return new DebianUpdater(deviceRegistrationProperties,
                scriptRunner,
                datasource,
                updateStatusObserver,
                updatesStartCommand,
                updatesRunningCommand,
                updatesDownloadStartCommand,
                updatesDownloadRunningCommand,
                updatesCheckCommand,
                updatesRunningInfoCommand,
                updatesFailedCommand,
                updatesRecoveryStartCommand,
                updatesRecoveryRunningCommand,
                pinEblockerListsCommand,
                unpinEblockerListsCommand,
                eBlockerListsPinningFilename,
                eBlockerListsPinningVersion,
                projectVersion);
    }

    @Test
    public void testGetLastUpdateTime() throws IOException, InterruptedException {
        DebianUpdater debianUpdate = createDebianUpdate();
        assertEquals(lastUpdateTime, debianUpdate.getLastUpdateTime());
    }

    @Test
    public void testStartUpdateWithoutLicense() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(false);

        thrown.expect(EblockerException.class);
        thrown.expectMessage("Running update scripts not possible, because the subscription of this device is not valid or it is not registered.");

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdate();
    }

    @Test
    public void testStartUpdateAlreadyRunningUpdate() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(true, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdate();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesStartCommand);
    }

    @Test
    public void testStartUpdateAlreadyRunningDownload() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, true);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdate();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesStartCommand);
    }

    @Test
    public void testStartUpdateSuccessfull() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdate();

        Mockito.verify(scriptRunner).runScript(updatesRunningCommand);
        Mockito.verify(datasource).saveLastUpdateTime(Mockito.any());
        Mockito.verify(updateStatusObserver).updateStarted(debianUpdate);
    }

    @Test
    public void testStartDownloadWithoutLicense() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(false);

        thrown.expect(EblockerException.class);
        thrown.expectMessage("Running update scripts not possible, because the subscription of this device is not valid or it is not registered.");

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startDownload();
    }

    @Test
    public void testStartDownloadAlreadyRunningUpdate() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(true, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startDownload();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesDownloadStartCommand);
    }

    @Test
    public void testStartDownloadAlreadyRunningDownload() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, true);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startDownload();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesDownloadStartCommand);
    }

    @Test
    public void testStartDownloadSuccessfull() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startDownload();

        Mockito.verify(scriptRunner).runScript(updatesDownloadStartCommand);
    }

    @Test
    public void testStartRecoveryAlreadyUpdating() throws IOException, InterruptedException {
        prepareScriptRunner(true, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdateRecovery();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesRecoveryStartCommand);
    }

    @Test
    public void testStartRecovery() throws IOException, InterruptedException {
        prepareScriptRunner(false, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdateRecovery();

        Mockito.verify(scriptRunner).runScript(updatesRecoveryStartCommand);
    }

    @Test
    public void testUpdatesAvailabledWithoutLicense() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(false);

        thrown.expect(EblockerException.class);
        thrown.expectMessage("Running update scripts not possible, because the subscription of this device is not valid or it is not registered.");

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.updatesAvailable();
    }

    @Test
    public void testUpdatesAvailableAlreadyRunningUpdate() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(true, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.updatesAvailable();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesCheckCommand);
    }

    @Test
    public void testUpdatesAvailableAlreadyRunningDownload() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, true);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.updatesAvailable();

        Mockito.verify(scriptRunner, Mockito.times(0)).runScript(updatesCheckCommand);
    }

    @Test
    public void testStartUpdatesAvailableCheck() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.updatesAvailable();

        Mockito.verify(scriptRunner).startScript(updatesCheckCommand);
    }

    @Test
    public void testUpdatesAvailableYes() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(process.exitValue()).thenReturn(0);
        Mockito.when(process.pollStdout()).thenReturn(availableUpdatesString1, availableUpdatesString2, null);
        Mockito.when(scriptRunner.startScript(updatesCheckCommand)).thenReturn(process);

        DebianUpdater debianUpdate = createDebianUpdate();

        assertTrue(debianUpdate.updatesAvailable());

        List<String> packages = debianUpdate.updatesAvailablePackages();

        assertEquals(2, packages.size());
        assertTrue(packages.containsAll(Arrays.asList("kpartx", "libtiff5")));

        Mockito.verify(scriptRunner).startScript(updatesCheckCommand);
    }

    @Test
    public void testUpdatesAvailableWhileCheckIsRunning() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(process.isAlive()).thenReturn(true);
        Mockito.when(scriptRunner.startScript(updatesCheckCommand)).thenReturn(process);

        DebianUpdater debianUpdate = createDebianUpdate();

        assertFalse(debianUpdate.updatesAvailable());
        assertTrue(debianUpdate.updatesAvailablePackages().isEmpty());
    }

    @Test
    public void testUpdatesAvailableNo() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(process.exitValue()).thenReturn(1);
        Mockito.when(scriptRunner.startScript(updatesCheckCommand)).thenReturn(process);

        DebianUpdater debianUpdate = createDebianUpdate();
        assertFalse(debianUpdate.updatesAvailable());

        Mockito.verify(scriptRunner).startScript(updatesCheckCommand);
    }

    @Test
    public void testGetUpdateStatesUpdating() throws IOException, InterruptedException {
        prepareScriptRunner(true, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();

        assertEquals(SystemUpdater.State.UPDATING, debianUpdate.getUpdateStatus());
    }

    @Test
    public void testGetUpdateStatesDownloading() throws IOException, InterruptedException {
        prepareScriptRunner(false, false, true);

        DebianUpdater debianUpdate = createDebianUpdate();

        assertEquals(SystemUpdater.State.DOWNLOADING, debianUpdate.getUpdateStatus());
    }

    @Test
    public void testGetUpdateStatesChecking() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false, false, false);

        LoggingProcess updateCheckRunner = Mockito.mock(LoggingProcess.class);
        Mockito.when(updateCheckRunner.isAlive()).thenReturn(true);
        Mockito.when(scriptRunner.startScript(updatesCheckCommand)).thenReturn(updateCheckRunner);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.updatesAvailable();

        assertEquals(SystemUpdater.State.CHECKING, debianUpdate.getUpdateStatus());
    }

    @Test
    public void testGetUpdateStatesIdling() throws IOException, InterruptedException {
        prepareScriptRunner(false, false, false);

        DebianUpdater debianUpdate = createDebianUpdate();

        assertEquals(SystemUpdater.State.IDLING, debianUpdate.getUpdateStatus());

        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        LoggingProcess updateCheckRunner = Mockito.mock(LoggingProcess.class);
        Mockito.when(updateCheckRunner.isAlive()).thenReturn(false);
        Mockito.when(scriptRunner.startScript(updatesCheckCommand)).thenReturn(updateCheckRunner);

        debianUpdate.updatesAvailable();

        assertEquals(SystemUpdater.State.IDLING, debianUpdate.getUpdateStatus());
    }

    @Test
    public void testGetUpdateStatesRecovering() throws IOException, InterruptedException {
        prepareScriptRunner(false, true, false);

        DebianUpdater debianUpdate = createDebianUpdate();

        assertEquals(SystemUpdater.State.RECOVERING, debianUpdate.getUpdateStatus());
    }

    @Test
    public void testUpdateProgressNeverRun() throws IOException, InterruptedException {
        DebianUpdater debianUpdate = createDebianUpdate();

        assertTrue(debianUpdate.getUpdateProgress().isEmpty());
    }

    @Test
    public void testUpdateProgressAlreadyFinished() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false ,false, false);

        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(scriptRunner.startScript(updatesRunningInfoCommand)).thenReturn(process);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdate();
        Mockito.when(process.isAlive()).thenReturn(false);
        assertTrue(debianUpdate.getUpdateProgress().isEmpty());

        // Gather uncaptured output of (just finished) update process
        Mockito.when(process.pollStdout()).thenReturn(updateProgressString1, updateProgressString2, null);
        assertEquals(SystemUpdater.State.IDLING, debianUpdate.getUpdateStatus());
        assertTrue(debianUpdate.getUpdateProgress().size() == 4);
        Mockito.verify(scriptRunner).stopScript(process);
    }

    @Test
    public void testUpdateProgressRunning() throws IOException, InterruptedException {
        Mockito.when(deviceRegistrationProperties.isSubscriptionValid()).thenReturn(true);
        prepareScriptRunner(false ,false, false);

        LoggingProcess process = Mockito.mock(LoggingProcess.class);
        Mockito.when(process.isAlive()).thenReturn(true);
        Mockito.when(process.pollStdout()).thenReturn(updateProgressString1, updateProgressString2, null);
        Mockito.when(scriptRunner.startScript(updatesRunningInfoCommand)).thenReturn(process);

        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.startUpdate();

        assertTrue(debianUpdate.getUpdateProgress().size() == 4);

        Mockito.verify(scriptRunner).startScript(updatesRunningInfoCommand);
    }

    @Test
    public void testInvalidLicense() throws IOException, InterruptedException {
        DebianUpdater debianUpdate = createDebianUpdate();

        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.INVALID);
        assertTrue(debianUpdate.invalidLicense());

        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.REVOKED);
        assertTrue(debianUpdate.invalidLicense());

        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);
        assertFalse(debianUpdate.invalidLicense());

        Mockito.when(deviceRegistrationProperties.getRegistrationState()).thenReturn(RegistrationState.OK);
        assertFalse(debianUpdate.invalidLicense());
    }

    @Test
    public void testPinEblockerListsPackage() throws IOException, InterruptedException {
        DebianUpdater debianUpdate = createDebianUpdate();
        debianUpdate.pinEblockerListsPackage();
        String path = Paths.get(System.getProperty("java.io.tmpdir"), eBlockerListsPinningFilename).toString();
        File f = null;

        try {
            f = new File(path);
            assertTrue(f.exists() && !f.isDirectory());
            Mockito.verify(scriptRunner).runScript(pinEblockerListsCommand);

            String testPath = ClassLoader.getSystemResource(TEST_PATH).getPath();
            Path expectedPinningFile = FileSystems.getDefault().getPath(testPath, eBlockerListsPinningFilename);
            Path actualPinningFile = FileSystems.getDefault().getPath(path);
            assertArrayEquals(Files.readAllBytes(expectedPinningFile), Files.readAllBytes(actualPinningFile));
        } finally {
            if (f != null) {
                f.delete();
            }
        }
    }

    @Test
    public void testUnpinEblockerListsPackage() throws IOException, InterruptedException {
        DebianUpdater debianUpdate = createDebianUpdate();

        debianUpdate.unpinEblockerListsPackage();

        Mockito.verify(scriptRunner).runScript(unpinEblockerListsCommand);
    }

    void prepareScriptRunner(boolean updating, boolean recovering, boolean downloading) throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(updatesRunningCommand)).thenReturn(updating ? 0 : 1);
        Mockito.when(scriptRunner.runScript(updatesRecoveryRunningCommand)).thenReturn(recovering ? 0 : 1);
        Mockito.when(scriptRunner.runScript(updatesDownloadRunningCommand)).thenReturn(downloading ? 0 : 1);
    }
}
