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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.FileWriter;
import java.io.File;
import java.io.Writer;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import org.eblocker.server.common.data.IpAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.RecordedUrl;
import org.eblocker.server.common.util.StartRecordingRequestData;
import org.eblocker.server.common.util.TextLineProvider;
import org.eblocker.server.common.system.ScriptRunner;

public class RecordingServiceTest {
	@Before
	public void setUp(){

	}
	@After
	public void tearDown(){

	}
	@Test
	public void getRecordedDomainList(){
		// Mock ScriptRunner
		ScriptRunner scriptRunner = Mockito.mock(ScriptRunner.class);
		try {
			when(scriptRunner.runScript("processing-script.sh")).thenReturn(0);
		} catch (Exception e) {
		}

		// Mock TextLineProvider
		TextLineProvider lineProvider = Mockito.mock(TextLineProvider.class);
		List<String> handshakes = new Vector<String>();
		handshakes.add("1.2.3.4\t1234:1234:1234:1234:1234:1234:1234:1234\twww.firstserver.com\t1");
		handshakes.add("1.2.3.5\t1235:1235:1235:1235:1235:1235:1235:1235\twww.secondserver.de\t2");
		handshakes.add("2.2.2.2\t2222:2222:2222:2222:2222:2222:2222:2222\tftp.data.galore\t10");
		handshakes.add("2.2.2.3\t2223:2223:2223:2223:2223:2223:2223:2223\tftp.data.galore\t11");
		handshakes.add("2.2.2.4\t2224:2224:2224:2224:2224:2224:2224:2224\tftp.data.galore\t12");
		handshakes.add("3.3.3.3\t3333:3333:3333:3333:3333:3333:3333:3333\tpics.cdn.match.social\t20");
		handshakes.add("3.3.3.3\t3333:3333:3333:3333:3333:3333:3333:3333\tpics.cdn.match.social\t21");
		when(lineProvider.getLines("file-handshakes")).thenReturn(handshakes);
		List<String> data = new Vector<String>();
		data.add("1.2.3.4\t1234:1234:1234:1234:1234:1234:1234:1234\t1");
		data.add("1.2.3.6\t1236:1236:1236:1236:1236:1236:1236:1236\t3");
		data.add("2.2.2.2\t2222:2222:2222:2222:2222:2222:2222:2222\t10");
		data.add("2.2.2.2\t2222:2222:2222:2222:2222:2222:2222:2222\t10");
		data.add("2.2.2.3\t2222:2222:2222:2222:2222:2222:2222:2222\t11");
		data.add("2.2.2.3\t2222:2222:2222:2222:2222:2222:2222:2222\t11");
		data.add("2.2.2.4\t2222:2222:2222:2222:2222:2222:2222:2222\t12");
		data.add("2.2.2.4\t2222:2222:2222:2222:2222:2222:2222:2222\t12");
		data.add("3.3.3.3\t3333:3333:3333:3333:3333:3333:3333:3333\t20");
		when(lineProvider.getLines("file-data")).thenReturn(data);

		Clock clock = Clock.systemUTC();
		// Set up service to be tested with mocked services
		RecordingService recService = new RecordingService(100, 100,
				"/invalid/file", "file-handshakes", "file-data",
				null, scriptRunner, null, lineProvider, clock);

		List<RecordedUrl> result = recService.getRecordedDomainList();
//		verify(scriptRunner.runScript());
		assertNotNull(result);

		RecordedUrl entryFirst = new RecordedUrl("1.2.3.4", "www.firstserver.com");
		entryFirst.adjustWhitelistRecommendation(true);
		assertTrue(result.contains(entryFirst));

		RecordedUrl entrySecond = new RecordedUrl("1.2.3.5", "www.secondserver.de");
		entrySecond.adjustWhitelistRecommendation(false);
		assertTrue(result.contains(entrySecond));

		// Server with several IPs - could be any of the IPs
		RecordedUrl entryThirdA = new RecordedUrl("2.2.2.2", "ftp.data.galore");
		RecordedUrl entryThirdB = new RecordedUrl("2.2.2.3", "ftp.data.galore");
		RecordedUrl entryThirdC = new RecordedUrl("2.2.2.4", "ftp.data.galore");
		entryThirdA.adjustWhitelistRecommendation(true);
		entryThirdB.adjustWhitelistRecommendation(true);
		entryThirdC.adjustWhitelistRecommendation(true);
		assertTrue(result.contains(entryThirdA) || result.contains(entryThirdB)
				|| result.contains(entryThirdC));

		// Several connections, app data observed for only one connection
		RecordedUrl entryFourth = new RecordedUrl("3.3.3.3", "pics.cdn.match.social");
		entryFourth.adjustWhitelistRecommendation(false);
		assertTrue(result.contains(entryFourth));
	}

	@Test
	public void startStopStopped(){
		// Mock device
		Device device = Mockito.mock(Device.class);
		when(device.getIpAddresses()).thenReturn(Collections.singletonList(IpAddress.parse("1.2.3.4")));
		// Mock dataSource
		DataSource dataSource = Mockito.mock(DataSource.class);
		when(dataSource.getDevice(anyString())).thenReturn(device);
		// Mock ScriptRunner
		ScriptRunner scriptRunner = Mockito.mock(ScriptRunner.class);
		try {
			when(scriptRunner.runScript("recording_start", "1.2.3.4")).thenReturn(0);
			when(scriptRunner.runScript("recording_status")).thenReturn(0, -1);
			when(scriptRunner.runScript("recording_stop", "")).thenReturn(0);
		} catch (Exception e) {
		}
		// Mock App Module Service
		AppModuleService appModuleService = Mockito.mock(AppModuleService.class);
		doNothing().when(appModuleService).storeAndActivateEnabledState(anyInt(), anyBoolean());

		// RecordingRequest (1 minute, 100 MB)
		StartRecordingRequestData reqStart = new StartRecordingRequestData("start", "1234", 1, 100);

		// Set up service to be tested with mocked services
		String recordingFile = null, handshakesFile = null, appDataFile = null;
		try {
			recordingFile = File.createTempFile("tmp.", ".recording").toString();
			handshakesFile = File.createTempFile("tmp.", ".handshakes").toString();
			appDataFile = File.createTempFile("tmp.", "appdata").toString();
		} catch (Exception e1) {
		}
		Clock clock = Clock.systemUTC();
		RecordingService recService = new RecordingService(100, 100,
				recordingFile, handshakesFile, appDataFile, dataSource,
				scriptRunner, appModuleService, null, clock);

		// Start recording
		boolean resultStartRecording = recService.recordingStartStop(reqStart);
		try {
			verify(scriptRunner).runScript("recording_start", "1.2.3.4");
		} catch (Exception e) {
		}
		assertTrue(resultStartRecording);

		// Get status (should be running)
		boolean resultGetStatusRunning = recService.getRecordingStatus();
		assertTrue(resultGetStatusRunning);
		try {
			verify(scriptRunner).runScript("recording_status");
		} catch (Exception e) {
		}

		// Stop recording
		StartRecordingRequestData reqStop = new StartRecordingRequestData("stop", "", 1, 1);
		boolean resultStopRecording = recService.recordingStartStop(reqStop);
		try{
			verify(scriptRunner).runScript("recording_stop", "");
		} catch (Exception e) {
		}
		assertTrue(resultStopRecording);

		// Get status (should be stopped now)
		boolean resultGetStatusStopped = recService.getRecordingStatus();
		assertFalse(resultGetStatusStopped);
		try {
			verify(scriptRunner,times(2)).runScript("recording_status");
			verify(scriptRunner).runScript("recording_stop", "");
		} catch (Exception e) {
		}
		verify(appModuleService).storeAndActivateEnabledState(anyInt(), anyBoolean());
	}

	@Test
	public void stopAfterTime(){
		// Mock device
		Device device = Mockito.mock(Device.class);
		when(device.getIpAddresses()).thenReturn(Collections.singletonList(IpAddress.parse("1.2.3.4")));
		// Mock dataSource
		DataSource dataSource = Mockito.mock(DataSource.class);
		when(dataSource.getDevice(anyString())).thenReturn(device);
		// Mock ScriptRunner
		ScriptRunner scriptRunner = Mockito.mock(ScriptRunner.class);
		try {
			when(scriptRunner.runScript("recording_start", "1.2.3.4")).thenReturn(0);
			when(scriptRunner.runScript("recording_status")).thenReturn(0);
			when(scriptRunner.runScript("recording_stop", "")).thenReturn(0);
		} catch (Exception e) {
		}
		// Mock App Module Service
		AppModuleService appModuleService = Mockito.mock(AppModuleService.class);
		doNothing().when(appModuleService).storeAndActivateEnabledState(anyInt(), anyBoolean());

		// RecordingRequest (1 minute, 100 MB)
		StartRecordingRequestData reqStart = new StartRecordingRequestData(
				"start", "1234", 1, 100);

		// Mock Time
		Clock clock = Mockito.mock(Clock.class);
	    Instant first = Instant.ofEpochSecond(1000L);
	    Instant second = first.plusSeconds(1);
	    Instant thirdAndAfter = second.plusSeconds(64);
		when(clock.instant()).thenReturn(first, second, thirdAndAfter);

		// Set up service to be tested with mocked services
		String recordingFile = null, handshakesFile = null, appDataFile = null;
		try {
			recordingFile = File.createTempFile("tmp.", ".recording").toString();
			handshakesFile = File.createTempFile("tmp.", ".handshakes").toString();
			appDataFile = File.createTempFile("tmp.", "appdata").toString();
		} catch (Exception e1) {
		}

		RecordingService recService = new RecordingService(100, 100,
				recordingFile, handshakesFile, appDataFile, dataSource,
				scriptRunner, appModuleService, null, clock);

		// Start recording
		boolean resultStartRecording = recService.recordingStartStop(reqStart);
		try {
			verify(scriptRunner).runScript("recording_start", "1.2.3.4");
		} catch (Exception e) {
		}
		assertTrue(resultStartRecording);

		// Get status (should be running)
		boolean resultGetStatusRunning = recService.getRecordingStatus();
		assertTrue(resultGetStatusRunning);
		try {
			verify(scriptRunner).runScript("recording_status");
		} catch (Exception e) {
		}

		// Get status (should be stopped now)
		boolean resultGetStatusStopped = recService.getRecordingStatus();
		assertFalse(resultGetStatusStopped);
		try {
			verify(scriptRunner,times(2)).runScript("recording_status");
			verify(scriptRunner).runScript("recording_stop", "");
		} catch (Exception e) {
		}
		verify(appModuleService).storeAndActivateEnabledState(anyInt(), anyBoolean());
	}

	@Test
	public void stopAfterSize(){
		// Mock device
		Device device = Mockito.mock(Device.class);
		when(device.getIpAddresses()).thenReturn(Collections.singletonList(IpAddress.parse("1.2.3.4")));
		// Mock dataSource
		DataSource dataSource = Mockito.mock(DataSource.class);
		when(dataSource.getDevice(anyString())).thenReturn(device);
		// Mock ScriptRunner
		ScriptRunner scriptRunner = Mockito.mock(ScriptRunner.class);
		try {
			when(scriptRunner.runScript("recording_start", "1.2.3.4")).thenReturn(0);
			when(scriptRunner.runScript("recording_status")).thenReturn(0);
			when(scriptRunner.runScript("recording_stop", "")).thenReturn(0);
		} catch (Exception e) {
		}
		// Mock App Module Service
		AppModuleService appModuleService = Mockito.mock(AppModuleService.class);
		doNothing().when(appModuleService).storeAndActivateEnabledState(anyInt(), anyBoolean());

		// RecordingRequest (10 minutes, 1 MB)
		StartRecordingRequestData reqStart = new StartRecordingRequestData("start", "1234", 10, 1);

		// Set up service to be tested with mocked services
		String recordingFile = null, handshakesFile = null, appDataFile = null;
		try {
			recordingFile = File.createTempFile("tmp.", ".recording").toString();
			handshakesFile = File.createTempFile("tmp.", ".handshakes").toString();
			appDataFile = File.createTempFile("tmp.", "appdata").toString();
		} catch (Exception e1) {
		}
		Clock clock = Clock.systemUTC();
		RecordingService recService = new RecordingService(100, 100,
				recordingFile, handshakesFile, appDataFile, dataSource,
				scriptRunner, appModuleService, null, clock);

		// Start recording
		boolean resultStartRecording = recService.recordingStartStop(reqStart);
		try {
			verify(scriptRunner).runScript("recording_start", "1.2.3.4");
		} catch (Exception e) {
		}
		assertTrue(resultStartRecording);

		// Get status (should be running)
		boolean resultGetStatusRunning = recService.getRecordingStatus();
		assertTrue(resultGetStatusRunning);
		try {
			verify(scriptRunner).runScript("recording_status");
		} catch (Exception e) {
		}

		// fill file with data
		try {
			Writer wr = new FileWriter(recordingFile);
			for (int i = 0; i < 1024 * 1024; ++i) {
				wr.write("23");
			}
			wr.close();
		} catch (Exception e) {
		}

		// Get status (should be stopped now)
		boolean resultGetStatusStopped = recService.getRecordingStatus();
		assertFalse(resultGetStatusStopped);
		try {
			verify(scriptRunner,times(2)).runScript("recording_status");
			verify(scriptRunner).runScript("recording_stop", "");
		} catch (Exception e) {
		}
		verify(appModuleService).storeAndActivateEnabledState(anyInt(), anyBoolean());

	}
}
