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
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.ExitNodeCountry;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This class is opening a Telnet connection to the Tor Control-Port,
 * which should be activated in the 'torrc'-config file by including:
 * <p>
 * ControlPort 9051
 * CookieAuthentication 0
 * <p>
 * See https://github.com/torproject/torspec/blob/main/control-spec.txt for the usage of the control port. It can be used to control and
 * query information of the running Tor instance.
 * <p>
 * NOTE: To be able to specify certain exit nodes for tor to use we need the TOR-GEOIPDB debian package!
 */
@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class TorController {
    private static final Logger log = LoggerFactory.getLogger(TorController.class);

    //Control port constants
    private final int torControlPort;
    private static final String LOGIN = "authenticate \"0zisGcn6wyC8o75hqMCLQnBibXePwcQadfwghoDQhURf82ThJu\""; //the hashed password (which has to be put into the tor config) can be generated with tor --hash-password PASSWORD

    public static final String NEW_IDENTITY_COMMAND = "signal newnym";
    public static final String RECONFIGURE_COMMAND = "signal reload";
    public static final String RESPONSE_OK = "250 OK";

    private final DataSource dataSource;
    private final EblockerDnsServer dnsServer;

    private boolean hasControlConnection = false;
    private boolean isCurrentlyChecking = false;

    private Set<String> currentExitNodeCountries; // a list of country names
    private TelnetConnection telnetConnection;
    private TorExitNodeCountries exitNodeCountries;
    private TorConfiguration configuration;
    private ScheduledFuture<?> connectionCheckingFuture;

    @Inject
    public TorController(@Named("tor.telnet.control.port") int torControlPort,
                         DataSource dataSource,
                         EblockerDnsServer dnsServer,
                         TelnetConnection telnetConnection,
                         TorExitNodeCountries exitNodeCountries,
                         TorConfiguration configuration
    ) {

        this.torControlPort = torControlPort;
        this.dataSource = dataSource;
        this.dnsServer = dnsServer;
        this.telnetConnection = telnetConnection;

        this.exitNodeCountries = exitNodeCountries;
        this.configuration = configuration;
    }

    @SubSystemInit
    public void init() {
        //load selected exit nodes countries
        this.currentExitNodeCountries = dataSource.getCurrentTorExitNodes();
        if (currentExitNodeCountries == null) {//if it does not exist in the Redis DB
            currentExitNodeCountries = new HashSet<>();
        }
    }

    /**
     * Establish the telnet connection to the Tor instance control port
     *
     * @return true if connected successful, false if not
     */
    private boolean initTorControlConnection() {
        log.info("Establishing telnet connection to Tor Control port");
        try {
            //Create telnet socket
            telnetConnection.connect("localhost", torControlPort);

            telnetConnection.writeLine(LOGIN);

            //check response
            String answer = telnetConnection.readLine();
            log.debug("Tor Control port authentication answer {}", answer);
            if (answer.equals(RESPONSE_OK)) {
                log.info("Connection to Tor control port succeeded!");
                return true;
            }
        } catch (IOException e) {
            log.warn("Initializing connection to Tor control port did not work.", e);
        }
        return false;
    }

    /**
     * Start checking the Tor connection with a delay in between the checks.
     * This will provide the buffer with the connection state (torNetworkConnection) for other objects
     * It automatically handles tries to reconnect to the Tor Control port, if the connection is not established (anymore).
     *
     * @param milliseconds delay in milliseconds between checks
     */
    public void startCheckingConnection(ScheduledExecutorService executor, long milliseconds) {
        if (!isCurrentlyChecking) {
            log.info("Preparing to check the Tor connection every {} milliseconds...", milliseconds);
            Runnable task = () -> {
                if (!hasControlConnection) {
                    hasControlConnection = initTorControlConnection();
                    if (!hasControlConnection) {
                        log.info("Tor control port not reachable...Trying to reinitalize connection to Tor control port...");
                    } else {
                        updateConfiguration();
                        if (connectionCheckingFuture != null) {
                            connectionCheckingFuture.cancel(false);
                        }
                    }
                }
            };
            connectionCheckingFuture = executor.scheduleWithFixedDelay(task, 0, milliseconds, TimeUnit.MILLISECONDS);
            isCurrentlyChecking = true;
        }
    }

    //-----------------------------
    //Tor Exit Nodes / Country management

    /**
     * Get a set of all exit nodes countries
     *
     * @return
     */
    public Set<ExitNodeCountry> getCountryList() {
        return exitNodeCountries.getExitNodeCountries();
    }

    /**
     * Reconfigures the Tor instance to use only exit nodes in the given set of countries
     *
     * @param selectedCountries Set of country names that should be used as exit nodes
     */
    public void setAllowedExitNodesCountries(Set<String> selectedCountries) {
        if (selectedCountries != null) {
            currentExitNodeCountries = selectedCountries;

            //save current Tor exit nodes
            dataSource.saveCurrentTorExitNodes(currentExitNodeCountries);

            updateConfiguration();
        }
    }

    private void updateConfiguration() {
        // From the frontend, only codes are given and no need to translate
        // country names (in whatever language) back to their respective codes
        Set<String> countryCodes = currentExitNodeCountries;
        configuration.update(countryCodes);
        tellTorToReconfigure();
    }

    /**
     * Return the current defined countries that the Tor instance should choose exit nodes in
     *
     * @return
     */
    public Set<String> getCurrentExitNodeCountries() {
        return currentExitNodeCountries;
    }

    /**
     * This tells Tor to create a new circuit, and therefore we will also   get another IP-address (exit node)
     */
    public boolean getNewIdentity() {
        return sendCommand(NEW_IDENTITY_COMMAND);
    }

    /**
     * Tell Tor to reconfigure with its configuration file
     *
     * @return true if the reconfiguration worked; false otherwise
     */
    private boolean tellTorToReconfigure() {
        return sendCommand(RECONFIGURE_COMMAND);
    }

    private boolean sendCommand(String command) {
        log.debug("Attempting to send command '{}'", command);
        return Boolean.TRUE.equals(tryWithReconnectionAttempt(() -> {
            telnetConnection.writeLine(command);
            String result = telnetConnection.readLine();
            if (result == null) {
                throw new IOException("Could not read response for command: " + command);
            }
            return result.equals(RESPONSE_OK);
        }));
    }

    // A Supplier that might throw an IOException:
    public interface IOSupplier<T> {
        T get() throws IOException;
    }

    private <T> T tryWithReconnectionAttempt(IOSupplier<T> action) {
        try {
            return action.get(); // first attempt
        } catch (IOException e) {
            log.error("Failed to communicate with control port. Will try to reconnect...", e);
            initTorControlConnection();
            try {
                return action.get(); // second attempt
            } catch (IOException e2) {
                log.error("Failed to communicate with control port. Giving up after one reconnection attempt.", e2);
                return null;
            }
        }
    }

    /**
     * Close the telnet connection properly.
     */
    public void shutdown() {
        try {
            telnetConnection.close();
        } catch (IOException e) {
            log.error("Error closing telnet connection to tor control.", e);
        }
    }

    //-----Device routing management
    public void addDeviceUsingTor(Device device) {
        dnsServer.useTorResolver(device);
    }

    public void removeDeviceNotUsingTor(Device device) {
        dnsServer.useDefaultResolver(device);
    }

}
