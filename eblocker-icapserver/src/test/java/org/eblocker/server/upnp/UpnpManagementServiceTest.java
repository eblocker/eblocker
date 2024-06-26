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
package org.eblocker.server.upnp;

import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.action.ActionArgumentValue;
import org.jupnp.model.action.ActionException;
import org.jupnp.model.meta.Action;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.UnsignedIntegerTwoBytes;
import org.jupnp.registry.Registry;
import org.jupnp.support.model.PortMapping.Protocol;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class UpnpManagementServiceTest {
    UpnpManagementService upnpManagementService;
    // Mocks
    NetworkInterfaceWrapper networkInterfaceWrapper;
    // Mock for cling
    UpnpServiceImpl upnpService;
    // Mocks for components and services UpnpManagementService makes use of
    UpnpPortForwardingAddFactory upnpPortForwardingAddFactory;
    UpnpPortForwardingDeleteFactory upnpPortForwardingDeleteFactory;
    UpnpActionCallbackFactory upnpActionCallbackFactory;
    UpnpActionInvocationFactory upnpActionInvocationFactory;
    private int maxSteps = 5;
    private int waitingTime = 10;

    @Before
    public void setup() {
        networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);
        ControlPoint controlPoint = Mockito.mock(ControlPoint.class);
        upnpService = Mockito.mock(UpnpServiceImpl.class);
        Mockito.when(upnpService.getControlPoint()).thenReturn(controlPoint, controlPoint);

        upnpPortForwardingAddFactory = Mockito.mock(UpnpPortForwardingAddFactory.class);
        upnpPortForwardingDeleteFactory = Mockito.mock(UpnpPortForwardingDeleteFactory.class);
        upnpActionCallbackFactory = Mockito.mock(UpnpActionCallbackFactory.class);
        upnpActionInvocationFactory = Mockito.mock(UpnpActionInvocationFactory.class);

        upnpManagementService = new UpnpManagementService(networkInterfaceWrapper, upnpService, upnpPortForwardingAddFactory,
                upnpPortForwardingDeleteFactory, upnpActionCallbackFactory, upnpActionInvocationFactory, maxSteps, waitingTime);
    }

    @Test
    public void testAddPortForwardingsNoDeviceFound() {
        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(Collections.emptyList());

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);
        int externalPortB = 3000;
        int internalPortB = 3001;
        String internalHostIpB = "192.168.0.42";
        int durationSecondsB = 0;
        String descriptionB = "descriptionB";
        Protocol protocolB = Protocol.UDP;
        boolean permanentB = true;
        UpnpPortForwarding forwardingB = new UpnpPortForwarding(externalPortB, internalPortB, internalHostIpB,
                durationSecondsB, descriptionB, protocolB, permanentB);
        forwardings.add(forwardingA);
        forwardings.add(forwardingB);

        List<UpnpPortForwardingResult> results = upnpManagementService.addPortForwardings(forwardings);

        Assert.assertTrue(results.size() == 2);
        // Check both failed
        boolean tcpForwardingFailed = false;
        boolean udpForwardingFailed = false;
        for (UpnpPortForwardingResult result : results) {
            Assert.assertFalse(result.isSuccess());
            Assert.assertEquals(result.getErrorMsg(), "No device found");
            if (result.getCorrespondingPortForwarding().getProtocol() == Protocol.UDP) {
                udpForwardingFailed = true;
            } else {
                tcpForwardingFailed = true;
            }
        }
        Assert.assertTrue(tcpForwardingFailed);
        Assert.assertTrue(udpForwardingFailed);
        // Check all relevant mocks were called

        // Mock called until timeout reached, for every forwarding
        Mockito.verify(upnpRegistry, Mockito.times(maxSteps * forwardings.size()))
                .getDevices(Mockito.any(DeviceType.class));
    }

    @Test
    public void testAddPortForwardingsGatewayDoesNotAllow() {
        // No device
        Device[] devices = new Device[0];

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(Mockito.any(DeviceType.class))).thenReturn(devices);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Collection<Device> gatewayDevices = new ArrayList<>();
        gatewayDevices.add(gatewayDevice);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDevices);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);
        int externalPortB = 3000;
        int internalPortB = 3001;
        String internalHostIpB = "192.168.0.42";
        int durationSecondsB = 0;
        String descriptionB = "descriptionB";
        Protocol protocolB = Protocol.UDP;
        boolean permanentB = true;
        UpnpPortForwarding forwardingB = new UpnpPortForwarding(externalPortB, internalPortB, internalHostIpB,
                durationSecondsB, descriptionB, protocolB, permanentB);
        forwardings.add(forwardingA);
        forwardings.add(forwardingB);

        List<UpnpPortForwardingResult> results = upnpManagementService.addPortForwardings(forwardings);

        Assert.assertTrue(results.size() == 2);
        // Check both failed
        boolean tcpForwardingFailed = false;
        boolean udpForwardingFailed = false;
        for (UpnpPortForwardingResult result : results) {
            Assert.assertFalse(result.isSuccess());
            Assert.assertEquals(result.getErrorMsg(), "No device found");
            if (result.getCorrespondingPortForwarding().getProtocol() == Protocol.UDP) {
                udpForwardingFailed = true;
            } else {
                tcpForwardingFailed = true;
            }
        }
        Assert.assertTrue(tcpForwardingFailed);
        Assert.assertTrue(udpForwardingFailed);
        // Check all relevant mocks were called

        Mockito.verify(upnpRegistry, Mockito.times(2)).getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice, Mockito.times(2)).findDevices(Mockito.any(DeviceType.class));

    }

    @Test
    public void testAddPortForwardings() {
        // First PortForwarding, via TCP
        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);

        forwardings.add(forwardingA);

        Service service = Mockito.mock(Service.class);

        // PortForwardingAdd used to install ForwardingA
        UpnpPortForwardingAdd upnpPortForwardingAddA = Mockito.mock(UpnpPortForwardingAdd.class);
        UpnpPortForwardingResult resultA = new UpnpPortForwardingResult(forwardingA, true, "");
        Mockito.when(upnpPortForwardingAddA.getResult()).thenReturn(resultA);
        // The method "success" would be called by cling
        Mockito.doAnswer(new Answer<Void>() {
                             @Override
                             public Void answer(InvocationOnMock invocation) throws Throwable {
                                 return null;
                             }
                         }
        ).when(upnpPortForwardingAddA).run();

        Mockito.when(upnpPortForwardingAddFactory.create(Mockito.any(Service.class), Mockito.any(ControlPoint.class),
                Mockito.any(UpnpPortForwarding.class), Mockito.any(UpnpManagementService.class)))
                .thenReturn(upnpPortForwardingAddA);

        Device connectionDevice = Mockito.mock(Device.class);
        // First attempt fails
        Mockito.when(connectionDevice.findService(UpnpManagementService.IP_SERVICE_TYPE)).thenReturn(null);
        // So the fallback is tested
        Mockito.when(connectionDevice.findService(UpnpManagementService.PPP_SERVICE_TYPE)).thenReturn(service);

        Device connectionDevices[] = new Device[1];
        connectionDevices[0] = connectionDevice;

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE))
                .thenReturn(connectionDevices);

        List<Device> gatewayDeviceList = new ArrayList<>();
        gatewayDeviceList.add(gatewayDevice);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDeviceList);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwardingResult> results = upnpManagementService.addPortForwardings(forwardings);

        // Check the result
        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0).isSuccess());
        Assert.assertEquals(results.get(0).getCorrespondingPortForwarding().getProtocol(), Protocol.TCP);

        // Make sure the relevant mocks have been called
        Mockito.verify(upnpRegistry)
                .getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice).findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.IP_SERVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.PPP_SERVICE_TYPE);

        Mockito.verify(upnpPortForwardingAddA).run();
        Mockito.verify(upnpPortForwardingAddA).getResult();
    }

    @Test
    public void testAddSamePortForwardingCausesUpdate() {
        // First PortForwarding, via TCP
        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);

        forwardings.add(forwardingA);

        Service service = Mockito.mock(Service.class);

        // PortForwardingAdd used to install ForwardingA
        UpnpPortForwardingAdd upnpPortForwardingAddA = Mockito.mock(UpnpPortForwardingAdd.class);
        UpnpPortForwardingResult resultA = new UpnpPortForwardingResult(forwardingA, true, "");
        Mockito.when(upnpPortForwardingAddA.getResult()).thenReturn(resultA);
        // The method "success" would be called by cling
        Mockito.doAnswer(new Answer<Void>() {
                             @Override
                             public Void answer(InvocationOnMock invocation) throws Throwable {
                                 return null;
                             }
                         }
        ).when(upnpPortForwardingAddA).run();

        Mockito.when(upnpPortForwardingAddFactory.create(Mockito.any(Service.class), Mockito.any(ControlPoint.class),
                Mockito.any(UpnpPortForwarding.class), Mockito.any(UpnpManagementService.class)))
                .thenReturn(upnpPortForwardingAddA);

        Device connectionDevice = Mockito.mock(Device.class);
        // First attempt fails
        Mockito.when(connectionDevice.findService(UpnpManagementService.IP_SERVICE_TYPE)).thenReturn(null);
        // So the fallback is tested
        Mockito.when(connectionDevice.findService(UpnpManagementService.PPP_SERVICE_TYPE)).thenReturn(service);

        Device connectionDevices[] = new Device[1];
        connectionDevices[0] = connectionDevice;

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE)).thenReturn(connectionDevices);

        List<Device> gatewayDeviceList = new ArrayList<>();
        gatewayDeviceList.add(gatewayDevice);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDeviceList);// +1

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);// +1

        List<UpnpPortForwardingResult> results = upnpManagementService.addPortForwardings(forwardings);

        // Check the result
        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0).isSuccess());
        Assert.assertEquals(results.get(0).getCorrespondingPortForwarding().getProtocol(), Protocol.TCP);

        // Make sure the relevant mocks have been called
        Mockito.verify(upnpRegistry)
                .getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice).findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.IP_SERVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.PPP_SERVICE_TYPE);

        Mockito.verify(upnpPortForwardingAddA).run();
        Mockito.verify(upnpPortForwardingAddA).getResult();

        // Now add a second port forwarding, pretty much the same, only diverging in the description

        // Second PortForwarding
        List<UpnpPortForwarding> forwardingsNew = new ArrayList<>();
        String descriptionANew = "descriptionA-New";
        UpnpPortForwarding forwardingANew = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionANew, protocolA, permanentA);

        forwardingsNew.add(forwardingANew);

        UpnpPortForwardingResult resultANew = new UpnpPortForwardingResult(forwardingANew, true, "a");
        Mockito.when(upnpPortForwardingAddA.getResult()).thenReturn(resultANew);

        List<UpnpPortForwardingResult> resultsNew = upnpManagementService.addPortForwardings(forwardingsNew);
        UpnpPortForwardingResult installedForwarding = resultsNew.get(0);
        Assert.assertTrue(installedForwarding.isSuccess());
        Assert.assertEquals(installedForwarding.getCorrespondingPortForwarding().getDescription(), descriptionANew);
    }

    @Test
    public void testRemovePortForwardingsNoDeviceFound() {
        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(Collections.emptyList());

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);
        int externalPortB = 3000;
        int internalPortB = 3001;
        String internalHostIpB = "192.168.0.42";
        int durationSecondsB = 0;
        String descriptionB = "descriptionB";
        Protocol protocolB = Protocol.UDP;
        boolean permanentB = true;
        UpnpPortForwarding forwardingB = new UpnpPortForwarding(externalPortB, internalPortB, internalHostIpB,
                durationSecondsB, descriptionB, protocolB, permanentB);
        forwardings.add(forwardingA);
        forwardings.add(forwardingB);

        List<UpnpPortForwardingResult> results = upnpManagementService.removePortForwardings(forwardings);

        Assert.assertTrue(results.size() == 2);
        // Check both failed
        boolean tcpRemovalFailed = false;
        boolean udpRemovalFailed = false;
        for (UpnpPortForwardingResult result : results) {
            Assert.assertFalse(result.isSuccess());
            Assert.assertEquals(result.getErrorMsg(), "No device found");
            if (result.getCorrespondingPortForwarding().getProtocol() == Protocol.UDP) {
                udpRemovalFailed = true;
            } else {
                tcpRemovalFailed = true;
            }
        }
        Assert.assertTrue(tcpRemovalFailed);
        Assert.assertTrue(udpRemovalFailed);
        // Check all relevant mocks were called

        // Mock called until timeout reached, for every forwarding
        Mockito.verify(upnpRegistry, Mockito.times(maxSteps * forwardings.size()))
                .getDevices(Mockito.any(DeviceType.class));
    }

    @Test
    public void testRemovePortForwardingsGatewayDoesNotAllow() {
        // No device
        Device[] devices = new Device[0];

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(Mockito.any(DeviceType.class))).thenReturn(devices);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Collection<Device> gatewayDevices = new ArrayList<>();
        gatewayDevices.add(gatewayDevice);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDevices);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);
        int externalPortB = 3000;
        int internalPortB = 3001;
        String internalHostIpB = "192.168.0.42";
        int durationSecondsB = 0;
        String descriptionB = "descriptionB";
        Protocol protocolB = Protocol.UDP;
        boolean permanentB = true;
        UpnpPortForwarding forwardingB = new UpnpPortForwarding(externalPortB, internalPortB, internalHostIpB,
                durationSecondsB, descriptionB, protocolB, permanentB);
        forwardings.add(forwardingA);
        forwardings.add(forwardingB);

        List<UpnpPortForwardingResult> results = upnpManagementService.removePortForwardings(forwardings);

        Assert.assertTrue(results.size() == 2);
        // Check both failed
        boolean tcpRemovalFailed = false;
        boolean udpRemovalFailed = false;
        for (UpnpPortForwardingResult result : results) {
            Assert.assertFalse(result.isSuccess());
            Assert.assertEquals(result.getErrorMsg(), "No device found");
            if (result.getCorrespondingPortForwarding().getProtocol() == Protocol.UDP) {
                udpRemovalFailed = true;
            } else {
                tcpRemovalFailed = true;
            }
        }
        Assert.assertTrue(tcpRemovalFailed);
        Assert.assertTrue(udpRemovalFailed);
        // Check all relevant mocks were called

        Mockito.verify(upnpRegistry, Mockito.times(2)).getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice, Mockito.times(2)).findDevices(Mockito.any(DeviceType.class));

    }

    @Test
    public void testRemovePortForwardings() {
        // First PortForwarding, via TCP
        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);

        forwardings.add(forwardingA);

        Service service = Mockito.mock(Service.class);

        // PortForwardingDelete used to remove ForwardingA
        UpnpPortForwardingDelete upnpPortForwardingDeleteA = Mockito.mock(UpnpPortForwardingDelete.class);
        UpnpPortForwardingResult resultA = new UpnpPortForwardingResult(forwardingA, true, "");
        Mockito.when(upnpPortForwardingDeleteA.getResult()).thenReturn(resultA);
        // The method "success" would be called by cling
        Mockito.doAnswer(new Answer<Void>() {
                             @Override
                             public Void answer(InvocationOnMock invocation) throws Throwable {
                                 return null;
                             }
                         }
        ).when(upnpPortForwardingDeleteA).run();

        Mockito.when(upnpPortForwardingDeleteFactory.create(Mockito.any(Service.class), Mockito.any(ControlPoint.class),
                Mockito.any(UpnpPortForwarding.class), Mockito.any(UpnpManagementService.class)))
                .thenReturn(upnpPortForwardingDeleteA);

        Device connectionDevice = Mockito.mock(Device.class);
        // First attempt fails
        Mockito.when(connectionDevice.findService(UpnpManagementService.IP_SERVICE_TYPE)).thenReturn(null);
        // So the fallback is tested
        Mockito.when(connectionDevice.findService(UpnpManagementService.PPP_SERVICE_TYPE)).thenReturn(service);

        Device connectionDevices[] = new Device[1];
        connectionDevices[0] = connectionDevice;

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE)).thenReturn(connectionDevices);

        List<Device> gatewayDeviceList = new ArrayList<>();
        gatewayDeviceList.add(gatewayDevice);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDeviceList);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwardingResult> results = upnpManagementService.removePortForwardings(forwardings);

        // Check the result
        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0).isSuccess());
        Assert.assertEquals(results.get(0).getCorrespondingPortForwarding().getProtocol(), Protocol.TCP);

        // Make sure the relevant mocks have been called
        Mockito.verify(upnpRegistry).getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice).findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.IP_SERVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.PPP_SERVICE_TYPE);

        Mockito.verify(upnpPortForwardingDeleteA).run();
        Mockito.verify(upnpPortForwardingDeleteA).getResult();
    }

    @Test
    public void testRemovePortForwardingsAlreadyRemoved() {
        // First PortForwarding, via TCP
        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        int externalPortA = 2000;
        int internalPortA = 2001;
        String internalHostIpA = "192.168.0.42";
        int durationSecondsA = 0;
        String descriptionA = "descriptionA";
        Protocol protocolA = Protocol.TCP;
        boolean permanentA = true;
        UpnpPortForwarding forwardingA = new UpnpPortForwarding(externalPortA, internalPortA, internalHostIpA,
                durationSecondsA, descriptionA, protocolA, permanentA);

        forwardings.add(forwardingA);

        Service service = Mockito.mock(Service.class);

        // PortForwardingDelete used to remove ForwardingA
        UpnpPortForwardingDelete upnpPortForwardingDeleteA = Mockito.mock(UpnpPortForwardingDelete.class);
        UpnpPortForwardingResult resultA = new UpnpPortForwardingResult(forwardingA, false, "");
        Mockito.when(upnpPortForwardingDeleteA.getResult()).thenReturn(resultA);

        Mockito.doAnswer(new Answer<Void>() {
                             @Override
                             public Void answer(InvocationOnMock invocation) throws Throwable {
                                 return null;
                             }
                         }
        ).when(upnpPortForwardingDeleteA).run();

        Mockito.when(upnpPortForwardingDeleteFactory.create(Mockito.any(Service.class), Mockito.any(ControlPoint.class),
                Mockito.any(UpnpPortForwarding.class), Mockito.any(UpnpManagementService.class)))
                .thenReturn(upnpPortForwardingDeleteA);

        Device connectionDevice = Mockito.mock(Device.class);
        // First attempt fails
        Mockito.when(connectionDevice.findService(UpnpManagementService.IP_SERVICE_TYPE)).thenReturn(null);
        // So the fallback is tested
        Mockito.when(connectionDevice.findService(UpnpManagementService.PPP_SERVICE_TYPE)).thenReturn(service);

        Device connectionDevices[] = new Device[1];
        connectionDevices[0] = connectionDevice;

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE)).thenReturn(connectionDevices);

        List<Device> gatewayDeviceList = new ArrayList<>();
        gatewayDeviceList.add(gatewayDevice);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDeviceList);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        // After the failure, existing forwardings are queried from the router

        // Forwardings installed on the router (none)
        List<UpnpPortForwarding> forwardingsExisting = new ArrayList<>();

        UpnpActionCallback upnpActionCallback = Mockito.mock(UpnpActionCallback.class);

        Mockito.when(upnpActionCallbackFactory.create(Mockito.any(), Mockito.any())).thenReturn(upnpActionCallback, upnpActionCallback, upnpActionCallback);
        // Upon invocation, no forwarding can be found
        UpnpActionInvocation firstInvocation = Mockito.mock(UpnpActionInvocation.class);
        Mockito.when(firstInvocation.getFailure()).thenReturn(new ActionException(1, "a"));

        Mockito.when(upnpActionInvocationFactory.create(Mockito.any())).thenReturn(firstInvocation);

        Action getPortMappingEntryAction = Mockito.mock(Action.class);

        Mockito.when(service.getAction(Mockito.eq("GetGenericPortMappingEntry"))).thenReturn(getPortMappingEntryAction);

        List<UpnpPortForwardingResult> results = upnpManagementService.removePortForwardings(forwardings);

        // Check the result
        Assert.assertTrue(results.size() == 1);
        Assert.assertTrue(results.get(0).isSuccess());
        Assert.assertEquals(results.get(0).getCorrespondingPortForwarding().getProtocol(), Protocol.TCP);

        // Make sure the relevant mocks have been called (once for deleting,
        // once for getting all existing forwardings)
        Mockito.verify(upnpRegistry, Mockito.times(2)).getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice, Mockito.times(2)).findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE);
        Mockito.verify(connectionDevice, Mockito.times(2)).findService(UpnpManagementService.IP_SERVICE_TYPE);
        Mockito.verify(connectionDevice, Mockito.times(2)).findService(UpnpManagementService.PPP_SERVICE_TYPE);

        Mockito.verify(upnpPortForwardingDeleteA).run();
        Mockito.verify(upnpPortForwardingDeleteA).getResult();

    }

    @Test
    public void testGetForwardings() {
        // Forwardings installed on the router

        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        // First PortForwarding, via TCP
        int firstForwardingExternalPort = 2000;
        int firstForwardingInternalPort = 2001;
        String firstForwardingInternalHostIp = "192.168.0.42";
        int firstForwardingDurationSeconds = 0;
        String firstForwardingDescription = "firstForwardingDescription";
        Protocol firstForwardingProtocol = Protocol.TCP;
        String firstForwardingRemoteHost = "-";
        boolean firstForwardingPermanent = true;
        UpnpPortForwarding firstForwarding = new UpnpPortForwarding(firstForwardingExternalPort,
                firstForwardingInternalPort, firstForwardingInternalHostIp, firstForwardingDurationSeconds,
                firstForwardingDescription, firstForwardingProtocol, firstForwardingPermanent);

        forwardings.add(firstForwarding);

        // Second PortForwarding, via UDP (other attributes like forwardingA)
        int secondForwardingExternalPort = firstForwardingExternalPort;
        int secondForwardingInternalPort = firstForwardingInternalPort;
        String secondForwardingInternalHostIp = firstForwardingInternalHostIp;
        int secondForwardingDurationSeconds = firstForwardingDurationSeconds;
        String secondForwardingDescription = "secondForwardingDescription";
        Protocol secondForwardingProtocol = Protocol.UDP;
        String secondForwardingRemoteHost = "-";
        boolean secondForwardingPermanent = true;
        UpnpPortForwarding secondForwarding = new UpnpPortForwarding(secondForwardingExternalPort,
                secondForwardingInternalPort, secondForwardingInternalHostIp, secondForwardingDurationSeconds,
                secondForwardingDescription, secondForwardingProtocol, secondForwardingPermanent);

        forwardings.add(secondForwarding);

        UpnpActionCallback upnpActionCallback = Mockito.mock(UpnpActionCallback.class);

        Mockito.when(upnpActionCallbackFactory.create(Mockito.any(), Mockito.any())).thenReturn(upnpActionCallback, upnpActionCallback, upnpActionCallback);

        /*
         * The first invocation needs to return all data for the first forwarding
         */
        UpnpActionInvocation firstInvocation = Mockito.mock(UpnpActionInvocation.class);
        ActionArgumentValue firstForwardingActionArgumentValueDescription = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueDescription.getValue()).thenReturn(firstForwardingDescription);
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DESCRIPTION))).thenReturn(firstForwardingActionArgumentValueDescription);

        ActionArgumentValue firstForwardingActionArgumentValueDuration = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueDuration.getValue()).thenReturn(firstForwarding.getLeaseDurationSeconds());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DURATION))).thenReturn(firstForwardingActionArgumentValueDuration);

        ActionArgumentValue firstForwardingActionArgumentValueEnabled = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueEnabled.getValue()).thenReturn(firstForwarding.isEnabled());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_ENABLED))).thenReturn(firstForwardingActionArgumentValueEnabled);

        ActionArgumentValue firstForwardingActionArgumentValueExternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueExternalPort.getValue()).thenReturn(firstForwarding.getExternalPort());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_EXTERNAL_PORT))).thenReturn(firstForwardingActionArgumentValueExternalPort);

        ActionArgumentValue firstForwardingActionArgumentValueInternalClient = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueInternalClient.getValue()).thenReturn(firstForwarding.getInternalClient());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_CLIENT))).thenReturn(firstForwardingActionArgumentValueInternalClient);

        ActionArgumentValue firstForwardingActionArgumentValueInternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueInternalPort.getValue()).thenReturn(firstForwarding.getInternalPort());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_PORT))).thenReturn(firstForwardingActionArgumentValueInternalPort);

        ActionArgumentValue firstForwardingActionArgumentValueProtocol = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueProtocol.getValue()).thenReturn(String.valueOf(firstForwarding.getProtocol()));
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_PROTOCOL))).thenReturn(firstForwardingActionArgumentValueProtocol);

        ActionArgumentValue firstForwardingActionArgumentValueRemoteHost = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueRemoteHost.getValue()).thenReturn(firstForwarding.getRemoteHost());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_REMOTE_HOST))).thenReturn(firstForwardingActionArgumentValueRemoteHost);

        /*
         * The second invocation needs to return all data for the second forwarding
         */
        UpnpActionInvocation secondInvocation = Mockito.mock(UpnpActionInvocation.class);
        ActionArgumentValue secondForwardingActionArgumentValueDescription = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueDescription.getValue()).thenReturn(secondForwardingDescription);
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DESCRIPTION))).thenReturn(secondForwardingActionArgumentValueDescription);

        ActionArgumentValue secondForwardingActionArgumentValueDuration = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueDuration.getValue()).thenReturn(secondForwarding.getLeaseDurationSeconds());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DURATION))).thenReturn(secondForwardingActionArgumentValueDuration);

        ActionArgumentValue secondForwardingActionArgumentValueEnabled = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueEnabled.getValue()).thenReturn(secondForwarding.isEnabled());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_ENABLED))).thenReturn(secondForwardingActionArgumentValueEnabled);

        ActionArgumentValue secondForwardingActionArgumentValueExternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueExternalPort.getValue()).thenReturn(secondForwarding.getExternalPort());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_EXTERNAL_PORT))).thenReturn(secondForwardingActionArgumentValueExternalPort);

        ActionArgumentValue secondForwardingActionArgumentValueInternalClient = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueInternalClient.getValue()).thenReturn(secondForwarding.getInternalClient());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_CLIENT))).thenReturn(secondForwardingActionArgumentValueInternalClient);

        ActionArgumentValue secondForwardingActionArgumentValueInternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueInternalPort.getValue()).thenReturn(secondForwarding.getInternalPort());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_PORT))).thenReturn(secondForwardingActionArgumentValueInternalPort);

        ActionArgumentValue secondForwardingActionArgumentValueProtocol = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueProtocol.getValue()).thenReturn(String.valueOf(secondForwarding.getProtocol()));
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_PROTOCOL))).thenReturn(secondForwardingActionArgumentValueProtocol);

        ActionArgumentValue secondForwardingActionArgumentValueRemoteHost = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueRemoteHost.getValue()).thenReturn(secondForwarding.getRemoteHost());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_REMOTE_HOST))).thenReturn(secondForwardingActionArgumentValueRemoteHost);

        // Upon third invocation, no further forwarding can be found
        UpnpActionInvocation thirdInvocation = Mockito.mock(UpnpActionInvocation.class);
        Mockito.when(thirdInvocation.getFailure()).thenReturn(new ActionException(1, "a"));

        Mockito.when(upnpActionInvocationFactory.create(Mockito.any())).thenReturn(firstInvocation, secondInvocation,
                thirdInvocation);

        Action getPortMappingEntryAction = Mockito.mock(Action.class);

        Service service = Mockito.mock(Service.class);
        Mockito.when(service.getAction(Mockito.eq("GetGenericPortMappingEntry"))).thenReturn(getPortMappingEntryAction);

        Device connectionDevice = Mockito.mock(Device.class);
        // First attempt fails
        Mockito.when(connectionDevice.findService(UpnpManagementService.IP_SERVICE_TYPE)).thenReturn(null);
        // So the fallback is tested
        Mockito.when(connectionDevice.findService(UpnpManagementService.PPP_SERVICE_TYPE)).thenReturn(service);

        Device connectionDevices[] = new Device[1];
        connectionDevices[0] = connectionDevice;

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE))
                .thenReturn(connectionDevices);

        List<Device> gatewayDeviceList = new ArrayList<>();
        gatewayDeviceList.add(gatewayDevice);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDeviceList);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        List<UpnpPortForwarding> results = upnpManagementService.getPortForwardings();

        // Check the result
        Assert.assertTrue(results.size() == 2);
        Assert.assertEquals(firstForwardingDescription, results.get(0).getDescription());
        Assert.assertEquals(new UnsignedIntegerFourBytes(firstForwardingDurationSeconds), results.get(0).getLeaseDurationSeconds());
        Assert.assertEquals(true, results.get(0).isEnabled());
        Assert.assertEquals(new UnsignedIntegerTwoBytes(firstForwardingExternalPort), results.get(0).getExternalPort());
        Assert.assertEquals(firstForwardingInternalHostIp, results.get(0).getInternalClient());
        Assert.assertEquals(new UnsignedIntegerTwoBytes(firstForwardingInternalPort), results.get(0).getInternalPort());
        Assert.assertEquals(firstForwardingProtocol, results.get(0).getProtocol());
        Assert.assertEquals(firstForwardingRemoteHost, results.get(0).getRemoteHost());

        Assert.assertEquals(secondForwardingDescription, results.get(1).getDescription());
        Assert.assertEquals(new UnsignedIntegerFourBytes(secondForwardingDurationSeconds), results.get(1).getLeaseDurationSeconds());
        Assert.assertEquals(true, results.get(1).isEnabled());
        Assert.assertEquals(new UnsignedIntegerTwoBytes(secondForwardingExternalPort), results.get(1).getExternalPort());
        Assert.assertEquals(secondForwardingInternalHostIp, results.get(1).getInternalClient());
        Assert.assertEquals(new UnsignedIntegerTwoBytes(secondForwardingInternalPort), results.get(1).getInternalPort());
        Assert.assertEquals(secondForwardingProtocol, results.get(1).getProtocol());
        Assert.assertEquals(secondForwardingRemoteHost, results.get(1).getRemoteHost());

        // Make sure the relevant mocks have been called
        Mockito.verify(upnpRegistry)
                .getDevices(Mockito.any(DeviceType.class));
        Mockito.verify(gatewayDevice).findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.IP_SERVICE_TYPE);
        Mockito.verify(connectionDevice).findService(UpnpManagementService.PPP_SERVICE_TYPE);

        Mockito.verify(service).getAction(Mockito.eq("GetGenericPortMappingEntry"));
        Mockito.verify(upnpActionCallbackFactory, Mockito.times(3)).create(Mockito.any(), Mockito.any());

        Mockito.verify(firstInvocation).setInput(Mockito.eq("NewPortMappingIndex"),
                Mockito.eq(new UnsignedIntegerTwoBytes(0)));
        Mockito.verify(firstInvocation).getFailure();

        Mockito.verify(secondInvocation).setInput(Mockito.eq("NewPortMappingIndex"),
                Mockito.eq(new UnsignedIntegerTwoBytes(1)));
        Mockito.verify(secondInvocation).getFailure();

        Mockito.verify(thirdInvocation).setInput(Mockito.eq("NewPortMappingIndex"),
                Mockito.eq(new UnsignedIntegerTwoBytes(2)));
        Mockito.verify(thirdInvocation).getFailure();

        Mockito.verify(upnpActionCallback, Mockito.times(3)).run();

    }

    @Test
    public void testGetConflictingForwardings() {
        // Forwardings installed on the router
        String otherDeviceIp = "192.168.0.42";
        String eBlockerIp = "192.168.0.23";

        List<UpnpPortForwarding> forwardings = new ArrayList<>();
        // First PortForwarding, conflicting since this device already uses the desired external port
        int firstForwardingExternalPort = 2000;
        int firstForwardingInternalPort = 2001;
        String firstForwardingInternalHostIp = otherDeviceIp;
        int firstForwardingDurationSeconds = 0;
        String firstForwardingDescription = "firstForwardingDescription";
        Protocol firstForwardingProtocol = Protocol.TCP;
        boolean firstForwardingPermanent = true;
        UpnpPortForwarding firstForwarding = new UpnpPortForwarding(firstForwardingExternalPort,
                firstForwardingInternalPort, firstForwardingInternalHostIp, firstForwardingDurationSeconds,
                firstForwardingDescription, firstForwardingProtocol, firstForwardingPermanent);

        forwardings.add(firstForwarding);

        // Second PortForwarding, conflicting since another external port maps
        // to the desired internal port (some routers do not allow this)
        int secondForwardingExternalPort = 2001;
        int secondForwardingInternalPort = 2000;
        String secondForwardingInternalHostIp = eBlockerIp;
        int secondForwardingDurationSeconds = firstForwardingDurationSeconds;
        String secondForwardingDescription = "secondForwardingDescription";
        Protocol secondForwardingProtocol = Protocol.UDP;
        boolean secondForwardingPermanent = true;
        UpnpPortForwarding secondForwarding = new UpnpPortForwarding(secondForwardingExternalPort,
                secondForwardingInternalPort, secondForwardingInternalHostIp, secondForwardingDurationSeconds,
                secondForwardingDescription, secondForwardingProtocol, secondForwardingPermanent);

        forwardings.add(secondForwarding);

        UpnpActionCallback upnpActionCallback = Mockito.mock(UpnpActionCallback.class);

        Mockito.when(upnpActionCallbackFactory.create(Mockito.any(), Mockito.any())).thenReturn(upnpActionCallback, upnpActionCallback, upnpActionCallback);

        /*
         * The first invocation needs to return all data for the first forwarding
         */
        UpnpActionInvocation firstInvocation = Mockito.mock(UpnpActionInvocation.class);
        ActionArgumentValue firstForwardingActionArgumentValueDescription = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueDescription.getValue()).thenReturn(firstForwardingDescription);
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DESCRIPTION))).thenReturn(firstForwardingActionArgumentValueDescription);

        ActionArgumentValue firstForwardingActionArgumentValueDuration = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueDuration.getValue()).thenReturn(firstForwarding.getLeaseDurationSeconds());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DURATION))).thenReturn(firstForwardingActionArgumentValueDuration);

        ActionArgumentValue firstForwardingActionArgumentValueEnabled = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueEnabled.getValue()).thenReturn(firstForwarding.isEnabled());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_ENABLED))).thenReturn(firstForwardingActionArgumentValueEnabled);

        ActionArgumentValue firstForwardingActionArgumentValueExternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueExternalPort.getValue()).thenReturn(firstForwarding.getExternalPort());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_EXTERNAL_PORT))).thenReturn(firstForwardingActionArgumentValueExternalPort);

        ActionArgumentValue firstForwardingActionArgumentValueInternalClient = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueInternalClient.getValue()).thenReturn(firstForwarding.getInternalClient());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_CLIENT))).thenReturn(firstForwardingActionArgumentValueInternalClient);

        ActionArgumentValue firstForwardingActionArgumentValueInternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueInternalPort.getValue()).thenReturn(firstForwarding.getInternalPort());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_PORT))).thenReturn(firstForwardingActionArgumentValueInternalPort);

        ActionArgumentValue firstForwardingActionArgumentValueProtocol = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueProtocol.getValue()).thenReturn(String.valueOf(firstForwarding.getProtocol()));
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_PROTOCOL))).thenReturn(firstForwardingActionArgumentValueProtocol);

        ActionArgumentValue firstForwardingActionArgumentValueRemoteHost = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(firstForwardingActionArgumentValueRemoteHost.getValue()).thenReturn(firstForwarding.getRemoteHost());
        Mockito.when(firstInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_REMOTE_HOST))).thenReturn(firstForwardingActionArgumentValueRemoteHost);

        /*
         * The second invocation needs to return all data for the second forwarding
         */
        UpnpActionInvocation secondInvocation = Mockito.mock(UpnpActionInvocation.class);
        ActionArgumentValue secondForwardingActionArgumentValueDescription = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueDescription.getValue()).thenReturn(secondForwardingDescription);
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DESCRIPTION))).thenReturn(secondForwardingActionArgumentValueDescription);

        ActionArgumentValue secondForwardingActionArgumentValueDuration = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueDuration.getValue()).thenReturn(secondForwarding.getLeaseDurationSeconds());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_DURATION))).thenReturn(secondForwardingActionArgumentValueDuration);

        ActionArgumentValue secondForwardingActionArgumentValueEnabled = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueEnabled.getValue()).thenReturn(secondForwarding.isEnabled());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_ENABLED))).thenReturn(secondForwardingActionArgumentValueEnabled);

        ActionArgumentValue secondForwardingActionArgumentValueExternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueExternalPort.getValue()).thenReturn(secondForwarding.getExternalPort());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_EXTERNAL_PORT))).thenReturn(secondForwardingActionArgumentValueExternalPort);

        ActionArgumentValue secondForwardingActionArgumentValueInternalClient = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueInternalClient.getValue()).thenReturn(secondForwarding.getInternalClient());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_CLIENT))).thenReturn(secondForwardingActionArgumentValueInternalClient);

        ActionArgumentValue secondForwardingActionArgumentValueInternalPort = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueInternalPort.getValue()).thenReturn(secondForwarding.getInternalPort());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_INTERNAL_PORT))).thenReturn(secondForwardingActionArgumentValueInternalPort);

        ActionArgumentValue secondForwardingActionArgumentValueProtocol = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueProtocol.getValue()).thenReturn(String.valueOf(secondForwarding.getProtocol()));
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_PROTOCOL))).thenReturn(secondForwardingActionArgumentValueProtocol);

        ActionArgumentValue secondForwardingActionArgumentValueRemoteHost = Mockito.mock(ActionArgumentValue.class);
        Mockito.when(secondForwardingActionArgumentValueRemoteHost.getValue()).thenReturn(secondForwarding.getRemoteHost());
        Mockito.when(secondInvocation.getOutput(Mockito.eq(UpnpManagementService.RESULT_KEY_PORT_MAPPING_REMOTE_HOST))).thenReturn(secondForwardingActionArgumentValueRemoteHost);

        // Upon third invocation, no further forwarding can be found
        UpnpActionInvocation thirdInvocation = Mockito.mock(UpnpActionInvocation.class);
        Mockito.when(thirdInvocation.getFailure()).thenReturn(new ActionException(1, "a"));

        Mockito.when(upnpActionInvocationFactory.create(Mockito.any())).thenReturn(firstInvocation, secondInvocation,
                thirdInvocation);

        Action getPortMappingEntryAction = Mockito.mock(Action.class);

        Service service = Mockito.mock(Service.class);
        Mockito.when(service.getAction(Mockito.eq("GetGenericPortMappingEntry"))).thenReturn(getPortMappingEntryAction);

        Device connectionDevice = Mockito.mock(Device.class);
        // First attempt fails
        Mockito.when(connectionDevice.findService(UpnpManagementService.IP_SERVICE_TYPE)).thenReturn(null);
        // So the fallback is tested
        Mockito.when(connectionDevice.findService(UpnpManagementService.PPP_SERVICE_TYPE)).thenReturn(service);

        Device connectionDevices[] = new Device[1];
        connectionDevices[0] = connectionDevice;

        Device gatewayDevice = Mockito.mock(Device.class);
        Mockito.when(gatewayDevice.findDevices(UpnpManagementService.CONNECTION_DEVICE_TYPE))
                .thenReturn(connectionDevices);

        List<Device> gatewayDeviceList = new ArrayList<>();
        gatewayDeviceList.add(gatewayDevice);

        Registry upnpRegistry = Mockito.mock(Registry.class);
        Mockito.when(upnpRegistry.getDevices(Mockito.any(DeviceType.class))).thenReturn(gatewayDeviceList);

        Mockito.when(upnpService.getRegistry()).thenReturn(upnpRegistry);

        Mockito.when(networkInterfaceWrapper.getFirstIPv4Address()).thenReturn(Ip4Address.parse(eBlockerIp));

        // Scenario: The eBlocker tries to take forwarding away from another device
        int externalPort = 2000;
        int internalPort = 2000;
        List<UpnpPortForwarding> results = upnpManagementService.findExistingForwardingsBlockingRequest(externalPort, internalPort);

        // Check the result
        Assert.assertTrue(results.size() == 2);
    }

}
