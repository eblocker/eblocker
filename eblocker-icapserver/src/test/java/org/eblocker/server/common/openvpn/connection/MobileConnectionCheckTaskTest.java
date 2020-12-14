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
package org.eblocker.server.common.openvpn.connection;

import io.netty.channel.nio.NioEventLoopGroup;
import org.eblocker.registration.MobileConnectionCheck;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MobileConnectionCheckTaskTest {

    private static final int PORT = 12358;
    private static final int TIMEOUT_REQUESTS = 5;
    private static final int POLL_INTERVAL = 1;
    private static final int POLL_TRIES = 3;
    private static ExecutorService EXECUTOR_SERVICE;

    private DeviceRegistrationClient deviceRegistrationClient;
    private OpenVpnServerService openVpnServerService;
    private MobileConnectionCheckTask task;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    private int nextId = 0;
    private Map<String, MobileConnectionCheck> requestedTestsById = new HashMap<>();
    private CountDownLatch requestsReceivedSignal = new CountDownLatch(6);

    @BeforeClass
    public static void beforeClass() {
        EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void afterClass() {
        EXECUTOR_SERVICE.shutdownNow();
    }

    @Before
    public void setUp() {
        deviceRegistrationClient = Mockito.mock(DeviceRegistrationClient.class);
        Mockito.when(deviceRegistrationClient.getMobileConnectionCheck(Mockito.anyString())).then(im -> requestedTestsById.get(im.getArgument(0)));
        Mockito.when(deviceRegistrationClient.requestMobileConnectionCheck(
                Mockito.any(MobileConnectionCheck.Protocol.class), Mockito.eq(PORT), Mockito.any(byte[].class)))
                .then(im -> {
                    MobileConnectionCheck test = new MobileConnectionCheck(String.valueOf(nextId++), MobileConnectionCheck.State.PENDING, new Date(), im.getArgument(2), im.getArgument(0), "1.2.3.4", PORT);
                    requestedTestsById.put(test.getId(), test);
                    requestsReceivedSignal.countDown();
                    return test;
                });

        openVpnServerService = Mockito.mock(OpenVpnServerService.class);
        Mockito.when(openVpnServerService.getOpenVpnMappedPort()).thenReturn(PORT);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        task = new MobileConnectionCheckTask(
                TIMEOUT_REQUESTS,
                3,
                POLL_INTERVAL,
                POLL_TRIES,
                "udp, tcp",
                PORT,
                openVpnServerService,
                deviceRegistrationClient,
                bossGroup,
                workerGroup);
    }

    @After
    public void tearDown() {
        bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS);
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS);
    }

    @Test(timeout = 30000)
    public void testConnectionTestSuccess() throws Exception {
        Future<?> future = EXECUTOR_SERVICE.submit(task);

        // wait until all requests have been received
        requestsReceivedSignal.await();
        Assert.assertEquals(MobileConnectionCheckStatus.State.PENDING_REQUESTS, task.getStatus().getState());

        // respond to all requests (and mark them as successful if handler echoes them)
        for (MobileConnectionCheck test : requestedTestsById.values()) {
            byte[] response;
            if (MobileConnectionCheck.Protocol.TCP == test.getProtocol()) {
                response = sendTcpRequest(test.getSecret());
            } else {
                response = sendUdpRequest(test.getSecret());
            }
            Assert.assertArrayEquals(test.getSecret(), response);
            requestedTestsById.put(test.getId(), new MobileConnectionCheck(test.getId(), MobileConnectionCheck.State.SUCCESS, new Date(), test.getSecret(), test.getProtocol(), test.getIpAddress(), test.getPort()));
        }

        // wait for task to finish
        future.get();

        // check results
        Assert.assertEquals(MobileConnectionCheckStatus.State.SUCCESS, task.getStatus().getState());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesReceived());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesReceived());
    }

    @Test(timeout = 30000)
    public void testConnectionTestDifferentMappedPortSuccess() throws Exception {
        // reset device registration mock to accept requests on a different port
        final int routerPort = 2358;
        Mockito.reset(deviceRegistrationClient);
        Mockito.when(deviceRegistrationClient.getMobileConnectionCheck(Mockito.anyString())).then(im -> requestedTestsById.get(im.getArgument(0)));
        Mockito.when(deviceRegistrationClient.requestMobileConnectionCheck(
                Mockito.any(MobileConnectionCheck.Protocol.class), Mockito.eq(routerPort), Mockito.any(byte[].class)))
                .then(im -> {
                    MobileConnectionCheck test = new MobileConnectionCheck(String.valueOf(nextId++), MobileConnectionCheck.State.PENDING, new Date(), im.getArgument(2), im.getArgument(0), "1.2.3.4", PORT);
                    requestedTestsById.put(test.getId(), test);
                    requestsReceivedSignal.countDown();
                    return test;
                });
        Mockito.when(openVpnServerService.getOpenVpnMappedPort()).thenReturn(routerPort);

        Future<?> future = EXECUTOR_SERVICE.submit(task);

        // wait until all requests have been received
        requestsReceivedSignal.await();
        Assert.assertEquals(MobileConnectionCheckStatus.State.PENDING_REQUESTS, task.getStatus().getState());

        // respond to all requests (and mark them as successful if handler echoes them)
        for (MobileConnectionCheck test : requestedTestsById.values()) {
            byte[] response;
            if (MobileConnectionCheck.Protocol.TCP == test.getProtocol()) {
                response = sendTcpRequest(test.getSecret());
            } else {
                response = sendUdpRequest(test.getSecret());
            }
            Assert.assertArrayEquals(test.getSecret(), response);
            requestedTestsById.put(test.getId(), new MobileConnectionCheck(test.getId(), MobileConnectionCheck.State.SUCCESS, new Date(), test.getSecret(), test.getProtocol(), test.getIpAddress(), test.getPort()));
        }

        // wait for task to finish
        future.get();

        // check results
        Assert.assertEquals(MobileConnectionCheckStatus.State.SUCCESS, task.getStatus().getState());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesReceived());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesReceived());
    }

    @Test(timeout = 30000)
    public void testConnectionTestFailure() throws Exception {
        Future<?> future = EXECUTOR_SERVICE.submit(task);

        // wait until all requests have been received
        requestsReceivedSignal.await();
        Assert.assertEquals(MobileConnectionCheckStatus.State.PENDING_REQUESTS, task.getStatus().getState());

        // respond to all requests (and mark them as failed)
        for (MobileConnectionCheck test : requestedTestsById.values()) {
            byte[] response;
            if (MobileConnectionCheck.Protocol.TCP == test.getProtocol()) {
                response = sendTcpRequest(test.getSecret());
            } else {
                response = sendUdpRequest(test.getSecret());
            }
            Assert.assertArrayEquals(test.getSecret(), response);
            requestedTestsById.put(test.getId(), new MobileConnectionCheck(test.getId(), MobileConnectionCheck.State.FAILED, new Date(), test.getSecret(), test.getProtocol(), test.getIpAddress(), test.getPort()));
        }

        // await task being finished an check results
        future.get();

        Assert.assertEquals(MobileConnectionCheckStatus.State.FAILURE, task.getStatus().getState());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesReceived());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesReceived());
    }

    @Test(timeout = 30000)
    public void testConnectionTestCancel() throws InterruptedException {
        Future<?> future = EXECUTOR_SERVICE.submit(task);

        // wait until all requests have been received
        requestsReceivedSignal.await();
        Assert.assertEquals(MobileConnectionCheckStatus.State.PENDING_REQUESTS, task.getStatus().getState());

        future.cancel(true);

        // future.get does not wait on thread being finished after interruption but may throw CancellationException
        // right away. THis makes it unsuitable for synchronization and we have to poll
        while (MobileConnectionCheckStatus.State.CANCELED != task.getStatus().getState()) {
            Thread.sleep(100);
        }
    }

    @Test(timeout = 30000)
    public void testConnectionTestTimeoutRequests() throws Exception {
        long start = System.currentTimeMillis();

        Future<?> future = EXECUTOR_SERVICE.submit(task);

        // wait until all requests have been received
        requestsReceivedSignal.await();
        Assert.assertEquals(MobileConnectionCheckStatus.State.PENDING_REQUESTS, task.getStatus().getState());

        // await task being finished an check results
        future.get();
        long stop = System.currentTimeMillis();

        Assert.assertTrue(stop - start >= TIMEOUT_REQUESTS * 1000);

        Assert.assertEquals(MobileConnectionCheckStatus.State.TIMEOUT_REQUESTS, task.getStatus().getState());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesSent());
        Assert.assertEquals(0, task.getStatus().getTcpMessagesReceived());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesSent());
        Assert.assertEquals(0, task.getStatus().getUdpMessagesReceived());
    }

    @Test(timeout = 30000)
    public void testConnectionTestTimeoutResults() throws Exception {
        long start = System.currentTimeMillis();

        Future<?> future = EXECUTOR_SERVICE.submit(task);

        // wait until all requests have been received
        requestsReceivedSignal.await();
        Assert.assertEquals(MobileConnectionCheckStatus.State.PENDING_REQUESTS, task.getStatus().getState());

        // respond to all requests (but do no mark result)
        for (MobileConnectionCheck test : requestedTestsById.values()) {
            byte[] response;
            if (MobileConnectionCheck.Protocol.TCP == test.getProtocol()) {
                response = sendTcpRequest(test.getSecret());
            } else {
                response = sendUdpRequest(test.getSecret());
            }
            Assert.assertArrayEquals(test.getSecret(), response);
        }

        // await task being finished an check results
        future.get();
        long stop = System.currentTimeMillis();

        Assert.assertTrue(stop - start >= POLL_INTERVAL * 1000 * POLL_TRIES);

        requestedTestsById.values().forEach(test -> Mockito.verify(deviceRegistrationClient, Mockito.atLeast(POLL_TRIES)).getMobileConnectionCheck(test.getId()));

        Assert.assertEquals(MobileConnectionCheckStatus.State.TIMEOUT_RESULTS, task.getStatus().getState());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getTcpMessagesReceived());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesSent());
        Assert.assertEquals(3, task.getStatus().getUdpMessagesReceived());
    }

    private byte[] sendTcpRequest(byte[] payload) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", PORT));
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            byte[] response = new byte[payload.length];
            socket.getInputStream().read(response);
            return response;
        }
    }

    private byte[] sendUdpRequest(byte[] payload) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(payload, payload.length, new InetSocketAddress("127.0.0.1", PORT)));
            DatagramPacket responsePacket = new DatagramPacket(new byte[payload.length], payload.length);
            socket.receive(responsePacket);
            return responsePacket.getData();
        }
    }
}
