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

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class RoutingControllerTest {

    private static final String RT_PREFIX = "openvpn";
    private static final int RT_OFFSET = 100;
    private static final int ROUTE_MIN = 0;
    private static final int ROUTE_MAX = 9;
    private static final String RECONFIGURE_ROUTING_TABLES_SCRIPT = "reconfigureTables";
    private static final String RECONFIGURE_POLICY_SCRIPT = "reconfigurePolicy";
    private static final String VPN_SET_CLIENT_ROUTE_SCRIPT = "setClientRoute";
    private static final String VPN_CLEAR_CLIENT_ROUTE_SCRIPT = "clearClientRoute";

    private String rtTablesPath = "test-data/vpn/rt_tables";
    private String rtTablesExpectedPath = "test-data/vpn/rt_tables_expected";
    private File rtTablesTempFile;

    private RoutingController routingController;
    private ScriptRunner scriptRunner;

    @Before
    public void setup() throws IOException {
        rtTablesTempFile = File.createTempFile("rttables", ".tmp");
        rtTablesTempFile.deleteOnExit();

        scriptRunner = Mockito.mock(ScriptRunner.class);
        routingController = new RoutingController(rtTablesPath, rtTablesTempFile.getAbsolutePath(), RT_PREFIX, RT_OFFSET, ROUTE_MIN, ROUTE_MAX, RECONFIGURE_ROUTING_TABLES_SCRIPT, RECONFIGURE_POLICY_SCRIPT, VPN_SET_CLIENT_ROUTE_SCRIPT, VPN_CLEAR_CLIENT_ROUTE_SCRIPT, scriptRunner);
    }

    @Test
    public void setupRoutingTables() throws Exception {
        InOrder inOrder = Mockito.inOrder(scriptRunner);

        // check creation
        List<Integer> routes = new ArrayList<>();
        for(int i = 0; i <= ROUTE_MAX - ROUTE_MIN; ++i) {
            Integer route = routingController.createRoute();
            Assert.assertNotNull(route);

            routes.add(route);

            // check setup routing table script call
            inOrder.verify(scriptRunner).runScript(RECONFIGURE_ROUTING_TABLES_SCRIPT);

            // check policy configuration
            String[] arguments = createReconfigurePolicyArguments(routes, 0, i + 1);
            inOrder.verify(scriptRunner).runScript(RECONFIGURE_POLICY_SCRIPT, arguments);
        }

        // check generated routing tables
        List<String> expectedRtTablesLines = ResourceHandler.readLines(new SimpleResource(rtTablesExpectedPath));
        List<String> generatedRtTablesLines = ResourceHandler.readLines(new SimpleResource(rtTablesTempFile.getAbsolutePath()));

        Assert.assertEquals(expectedRtTablesLines.size(), generatedRtTablesLines.size());
        Iterator<String> i = expectedRtTablesLines.iterator();
        Iterator<String> j = generatedRtTablesLines.iterator();
        while (i.hasNext() && j.hasNext()) {
            Assert.assertEquals(i.next(), j.next());
        }

        // check tear-down
        int n = routes.size();
        for(int k = 0; k < n; ++k) {
            routingController.deleteRoute(routes.get(k));

            // check setup routing table script call
            inOrder.verify(scriptRunner).runScript(RECONFIGURE_ROUTING_TABLES_SCRIPT);

            // check policy configuration
            String[] arguments = createReconfigurePolicyArguments(routes, k + 1, n);
            inOrder.verify(scriptRunner).runScript(RECONFIGURE_POLICY_SCRIPT, arguments);
        }

    }

    private String[] createReconfigurePolicyArguments(List<Integer> routes, int startIndex, int endIndex) {
        int n = endIndex - startIndex;
        String[] arguments = new String[2 + n];
        arguments[0] = RT_PREFIX;
        arguments[1] = String.valueOf(RT_OFFSET);
        for(int i = 0; i < n; ++i) {
            arguments[2 + i] = String.valueOf(routes.get(startIndex + i));
        }
        return arguments;
    }

    @Test
    public void setClientRoute() throws IOException, InterruptedException {
        final String virtualInterfaceName = "tun0";
        final String routeNetGateway = "10.10.10.10";
        final String routeVpnGateway = "10.11.10.10";
        final String trustedIp = "9.9.9.9";
        int id = routingController.createRoute();
        routingController.setClientRoute(id, virtualInterfaceName, routeNetGateway, routeVpnGateway, trustedIp);

        // check set client route script call
        Mockito.verify(scriptRunner).runScript(VPN_SET_CLIENT_ROUTE_SCRIPT, String.valueOf(id), virtualInterfaceName, routeNetGateway, routeVpnGateway, trustedIp);
    }

    @Test
    public void clearClientRoute() throws IOException, InterruptedException {
        final String trustedIp = "9.9.9.9";
        int id = routingController.createRoute();
        routingController.clearClientRoute(id, trustedIp);

        // check clear client route script call
        Mockito.verify(scriptRunner).runScript(VPN_CLEAR_CLIENT_ROUTE_SCRIPT, String.valueOf(id), trustedIp);
    }

    @Test
    public void testNoDuplicateRoutes() {
        Set<Integer> routes = new HashSet<>();
        int n = ROUTE_MAX - ROUTE_MIN + 1;
        for(int i = 0; i < n; ++i) {
            Integer route = routingController.createRoute();
            Assert.assertNotNull(route);
            Assert.assertTrue(routes.add(route));
        }
    }

    @Test
    public void testAcquireReleaseCycle() {
        Queue<Integer> routes = new LinkedList<>();
        int n = ROUTE_MAX - ROUTE_MIN + 1;
        for(int i = 0; i < n; ++i) {
            Integer route = routingController.createRoute();
            Assert.assertNotNull(route);
            routes.add(route);
        }

        Assert.assertNull(routingController.createRoute());

        Integer route = routes.remove();
        routingController.deleteRoute(route);
        Assert.assertEquals(route, routingController.createRoute());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearUnallocatedRoute() {
        routingController.clearClientRoute(0, "");
        Mockito.verifyNoMoreInteractions(scriptRunner);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetUnallocatedRoute() {
        routingController.setClientRoute(0, "", "", "", "");
        Mockito.verifyNoMoreInteractions(scriptRunner);
    }



}