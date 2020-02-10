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
package org.eblocker.server.common;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.util.RemainingPause;
import org.eblocker.server.http.service.DeviceService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PauseDeviceControllerImplTest {

	private long pausingInterval = 300;
	private Device device;
	private NetworkStateMachine networkStateMachine;
	private DeviceService deviceService;

    private MockScheduledExecutorService executorService;

	@Before
	public void setUp() {
		device = TestDeviceFactory.createDevice("abcdef012345", "192.168.1.23", true);

        networkStateMachine = Mockito.mock(NetworkStateMachine.class);
		Mockito.doNothing().when(networkStateMachine).deviceStateChanged();
		Mockito.doNothing().when(networkStateMachine).deviceStateChanged(any(Device.class));

		deviceService = Mockito.mock(DeviceService.class);

		TestDeviceFactory tdf = new TestDeviceFactory(deviceService);
		tdf.addDevice(device);

		executorService = new MockScheduledExecutorService();
}

	@Test
	public void pauseDevice() {
		// Set up controller
		PauseDeviceController controller = new PauseDeviceController(
				networkStateMachine, executorService,
				deviceService);

		// Test: Unpaused device is unpaused
		RemainingPause resultUnpaused = controller.getRemainingPause(device);
		assertNotNull(resultUnpaused);
		assertEquals(Long.valueOf(0), resultUnpaused.getPausing());

		// Pause device
		RemainingPause resultPausing = controller.pauseDevice(device, pausingInterval);

		// Test: Paused device is paused
		assertNotNull(resultPausing);
		assertEquals(Long.valueOf(pausingInterval), resultPausing.getPausing());
		assertFalse(device.isEnabled());
		verify(deviceService).updateDevice(device);
		verify(networkStateMachine).deviceStateChanged(device);

		executorService.elapse(Duration.ofSeconds(pausingInterval));

		// Test: Expired pause is expired
		RemainingPause resultExpired = controller.getRemainingPause(device);
		assertNotNull(resultExpired);
		assertEquals(Long.valueOf(0), resultExpired.getPausing());
		assertTrue(device.isEnabled());
		verify(deviceService, times(2)).updateDevice(device);
		verify(networkStateMachine).deviceStateChanged();
	}

	@Test
	public void repauseDevice() {
		// Set up controller
		PauseDeviceController controller = new PauseDeviceController(
				networkStateMachine, executorService,
				deviceService);

		// Pause device
		RemainingPause resultPausing = controller.pauseDevice(device, pausingInterval);

		// Test: Paused device is paused
		assertNotNull(resultPausing);
		assertEquals(Long.valueOf(pausingInterval), resultPausing.getPausing());

		executorService.elapse(Duration.ofSeconds(pausingInterval));

		// Test: Expired pause is expired
		RemainingPause resultExpired = controller.getRemainingPause(device);
		assertNotNull(resultExpired);
		assertEquals(Long.valueOf(0), resultExpired.getPausing());

		// Pause device again
		RemainingPause resultRepausing = controller.pauseDevice(device, pausingInterval);

		// Paused again device is paused again
		assertNotNull(resultRepausing);
		assertEquals(Long.valueOf(pausingInterval), resultPausing.getPausing());
		assertFalse(device.isEnabled());
		verify(deviceService, times(3)).updateDevice(device);
		verify(networkStateMachine, times(2)).deviceStateChanged(device);

        executorService.elapse(Duration.ofSeconds(pausingInterval));

        // Test: Expired pause is expired (again)
		RemainingPause resultExpiredAgain = controller.getRemainingPause(device);
		assertNotNull(resultExpiredAgain);
		assertEquals(Long.valueOf(0), resultExpiredAgain.getPausing());
		assertTrue(device.isEnabled());
		verify(deviceService, times(4)).updateDevice(device);
		verify(networkStateMachine, times(2)).deviceStateChanged();
	}

	@Test
	public void extendPause() {
		long delayBeforeExtendingPause = 200;

		// Set up controller
		PauseDeviceController controller = new PauseDeviceController(
				networkStateMachine, executorService,
				deviceService);

		// Test: Unpaused device is unpaused
		RemainingPause resultUnpaused = controller.getRemainingPause(device);
		assertNotNull(resultUnpaused);
		assertEquals(Long.valueOf(0), resultUnpaused.getPausing());

		// Pause device
		RemainingPause resultPausing = controller.pauseDevice(device, pausingInterval);

		// Test: Paused device is paused
		assertNotNull(resultPausing);
		assertEquals(Long.valueOf(pausingInterval), resultPausing.getPausing());
		assertFalse(device.isEnabled());
		verify(deviceService).updateDevice(device);
		verify(networkStateMachine).deviceStateChanged(device);

		// Extend pause
		executorService.elapse(Duration.ofSeconds(delayBeforeExtendingPause));
		RemainingPause resultExtended = controller.pauseDevice(device, pausingInterval);
		assertNotNull(resultExtended);
		assertEquals(Long.valueOf(pausingInterval), resultExtended.getPausing());

		// Still pausing...
		executorService.elapse(Duration.ofSeconds(250));
		assertFalse(device.isEnabled());
	}

	@Test
	public void reenablePausedDevice() {
	    Device device = Mockito.mock(Device.class);
	    when(device.isUseAnonymizationService()).thenReturn(false);
	    when(device.isRoutedThroughTor()).thenReturn(false);
	    when(device.isSslEnabled()).thenReturn(false);

        // Set up controller
        PauseDeviceController controller = new PauseDeviceController(
                networkStateMachine, executorService,
                deviceService);

        controller.reactivateDevice(device);

        Mockito.verify(device).setEnabled(eq(true));
        Mockito.verify(device).setPaused(eq(false));
        Mockito.verify(deviceService).updateDevice(eq(device));
        Mockito.verify(networkStateMachine).deviceStateChanged();
	}

	@Test
	public void reenablePausedDeviceTorRouting() {
        Device device = Mockito.mock(Device.class);
        when(device.isUseAnonymizationService()).thenReturn(true);
        when(device.isRoutedThroughTor()).thenReturn(true);
        when(device.isSslEnabled()).thenReturn(false);

        // Set up controller
        PauseDeviceController controller = new PauseDeviceController(
                networkStateMachine, executorService,
                deviceService);

        controller.reactivateDevice(device);

        Mockito.verify(device).setEnabled(eq(true));
        Mockito.verify(device).setPaused(eq(false));
        Mockito.verify(deviceService).updateDevice(eq(device));
        Mockito.verify(networkStateMachine).deviceStateChanged();
	}

	@Test
	public void reenabledPausedDeviceSslEnabled() {
        Device device = Mockito.mock(Device.class);
        when(device.isUseAnonymizationService()).thenReturn(false);
        when(device.isRoutedThroughTor()).thenReturn(false);
        when(device.isSslEnabled()).thenReturn(true);

        // Set up controller
        PauseDeviceController controller = new PauseDeviceController(
                networkStateMachine, executorService,
                deviceService);

        controller.reactivateDevice(device);

        Mockito.verify(device).setEnabled(eq(true));
        Mockito.verify(device).setPaused(eq(false));
        Mockito.verify(deviceService).updateDevice(eq(device));
        Mockito.verify(networkStateMachine).deviceStateChanged();
	}

}
