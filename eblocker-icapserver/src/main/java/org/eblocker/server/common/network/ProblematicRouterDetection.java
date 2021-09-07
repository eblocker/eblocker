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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.messagecenter.provider.RouterCompatibilityMessageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Observable;

public class ProblematicRouterDetection extends Observable implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ProblematicRouterDetection.class);
    private static final String PROPERTY_KEY_DEFAULT_READ_TIMEOUT = "sun.net.client.defaultReadTimeout";
    private static final String PROPERTY_KEY_DEFAULT_CONNECT_TIMEOUT = "sun.net.client.defaultConnectTimeout";
    NetworkServices networkServices;
    RouterCompatibilityMessageProvider routerCompatibilityMessageProvider;

    @Inject
    public ProblematicRouterDetection(NetworkServices networkServices, RouterCompatibilityMessageProvider routerCompatibilityMessageProvider) {
        this.networkServices = networkServices;
        this.routerCompatibilityMessageProvider = routerCompatibilityMessageProvider;
    }

    /*
     * Find out if the router is a problematic router
     * @param ip The IP address of the router to test.
     * @return true is the router could be identified as a problematic router
     */
    private static boolean isDeviceProblematicRouter(String ip) throws Exception {
        /*
         * Test for FritzBox! models 7490, 7360 and 3490
         */
        URL fburl = new URL("http://" + ip + ":49000/tr64desc.xml");
        // set timeouts
        String connectTimeout = System.getProperty(PROPERTY_KEY_DEFAULT_CONNECT_TIMEOUT);
        String readTimeout = System.getProperty(PROPERTY_KEY_DEFAULT_READ_TIMEOUT);
        System.setProperty(PROPERTY_KEY_DEFAULT_CONNECT_TIMEOUT, "5000");
        System.setProperty(PROPERTY_KEY_DEFAULT_READ_TIMEOUT, "5000");

        boolean isFritzbox = false;

        try (BufferedReader in = new BufferedReader(new InputStreamReader(fburl.openStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("<modelDescription>FRITZ!Box 7490</modelDescription>")
                        || inputLine.contains("<modelDescription>FRITZ!Box 3490</modelDescription>")
                        || inputLine.contains("<modelDescription>FRITZ!Box 7360</modelDescription>")) {
                    isFritzbox = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Could not open a connection to the routers PnP-page.", e);
        } finally {
            // restore timeouts
            if (connectTimeout != null) {
                System.setProperty(PROPERTY_KEY_DEFAULT_CONNECT_TIMEOUT, connectTimeout);
            }
            if (readTimeout != null) {
                System.setProperty(PROPERTY_KEY_DEFAULT_READ_TIMEOUT, readTimeout);
            }
        }
        return isFritzbox;
    }

    @Override
    public void run() {
        // get address of gateway
        NetworkConfiguration networkConfiguration = networkServices
                .getCurrentNetworkConfiguration();
        String gatewayAddress = networkConfiguration.getGateway();
        if ((gatewayAddress != null)) {
            // detect router type
            try {
                // store information
                boolean isProblematicRouter = isDeviceProblematicRouter(gatewayAddress);
                this.routerCompatibilityMessageProvider
                        .setProblematicProvider(isProblematicRouter);
                setChanged();
                notifyObservers(isProblematicRouter);
            } catch (Exception e) {
                log.debug("Could not open a connection to the routers PnP-page.", e);
            }
        }
    }

}
