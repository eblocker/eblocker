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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is opening a Telnet connection to the Tor Control-Port,
 * which should be activated in the 'torrc'-config file by including:
 * <p>
 * ControlPort 9051
 * CookieAuthentication 0
 * <p>
 * See https://gitweb.torproject.org/torspec.git/tree/control-spec.txt for the usage of the control port. It can be used to control and
 * query information of the running Tor instance.
 * <p>
 * <p>
 * NOTE: To be able to specifiy certain exit nodes for tor to use we need the TOR-GEOIPDB debian package!!!
 * For the BananaPi M2 it can be found in this repository (mirror) :
 * deb http://mirrordirector.raspbian.org/raspbian/ wheezy main contrib non-free rpi
 */
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class TorController {
    private static final Logger log = LoggerFactory.getLogger(TorController.class);

    //Control port constants
    private final int torControlPort;
    private static final String LOGIN = "authenticate \"0zisGcn6wyC8o75hqMCLQnBibXePwcQadfwghoDQhURf82ThJu\""; //the hashed password (which has to be put into the tor config) can be generated with tor --hash-password PASSWORD
    public static final String STATUS_CIRCUIT_ESTABLISHED = "status/circuit-established";
    public static final String STATUS_ENOUGH_DIR_INFO = "status/enough-dir-info";
    public static final String STATUS_BOOTSTRAP_PHASE = "status/bootstrap-phase";

    public static final String SUBSCRIBE_STATUS_CLIENT_EVENTS = "setevents extended status_client";
    public static final String NEW_IDENTITY_COMMAND = "signal newnym";
    public static final String RECONFIGURE_COMMAND = "signal reload";
    public static final String RESPONSE_OK = "250 OK";
    public static final String TOR_EVENT_NOT_ENOUGH_DIR_INFO = "NOT_ENOUGH_DIR_INFO";
    public static final String TOR_EVENT_ENOUGH_DIR_INFO = "ENOUGH_DIR_INFO";
    public static final String TOR_EVENT_CIRCUIT_ESTABLISHED = "CIRCUIT_ESTABLISHED";
    private final Pattern statusClientEventPattern;

    private final DataSource dataSource;
    private final EblockerDnsServer dnsServer;

    private TorStatus status = TorStatus.BOOTSTRAPPING;
    private boolean torControlConnection = false;
    private boolean isCurrentlyChecking = false;

    private Set<String> currentExitNodeCountries; // a list of country names
    private TelnetConnection telnetConnection;
    private TorExitNodeCountries exitNodeCountries;
    private TorConfiguration configuration;

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

        this.statusClientEventPattern = Pattern.compile("650 STATUS_CLIENT NOTICE (\\w+)");

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

                telnetConnection.writeLine(SUBSCRIBE_STATUS_CLIENT_EVENTS);
                String subscribeAnswer = telnetConnection.readLine();

                if (subscribeAnswer.equals(RESPONSE_OK)) {
                    log.info("Subscribing to STATUS_CLIENT Tor events was successful!");
                    return true;
                }
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
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    if (!torControlConnection) {//connection to control port not ready
                        torControlConnection = initTorControlConnection();
                        if (!torControlConnection) {
                            log.info("Tor control port not reachable...Trying to reinitalize connection to Tor control port...");
                            return;
                        } else {
                            updateConfiguration();
                        }
                    }

                    if (status == TorStatus.BOOTSTRAPPING) {
                        if (isBootstrapPhaseComplete()) {
                            status = getCurrentTorStatus();
                        } else {
                            return; // continue with checking the bootstrap status
                        }
                    }

                    // Check if Tor is available currently
                    if (isEventAvailable()) {
                        log.debug("Processing Tor events...");
                        processStatusClientEvents();
                    }

                    log.debug("Tor client status: {}", status);

                }

            };
            executor.scheduleWithFixedDelay(task, 0, milliseconds, TimeUnit.MILLISECONDS);
            isCurrentlyChecking = true;
        }
    }

    private TorStatus getCurrentTorStatus() {
        boolean circuitEstablished = "1".equals(getInfo(STATUS_CIRCUIT_ESTABLISHED));
        boolean enoughDirInfo = "1".equals(getInfo(STATUS_ENOUGH_DIR_INFO));

        if (!enoughDirInfo) {
            return TorStatus.NOT_ENOUGH_DIR_INFO;
        }

        return circuitEstablished ? TorStatus.CIRCUIT_ESTABLISHED : TorStatus.READY;
    }

    private String getInfo(String status) {
        log.debug("Attempting to get info '{}'", status);
        return tryWithReconnectionAttempt(() -> {
            telnetConnection.writeLine("getinfo " + status);
            String expectedPrefix = "250-" + status + "=";
            String result = null;
            String response = telnetConnection.readLine();
            if (response == null) {
                throw new IOException("Could not read response for status info: " + status);
            }
            if (response.startsWith(expectedPrefix)) {
                result = response.substring(expectedPrefix.length());
            }
            String response2 = telnetConnection.readLine();
            if (response2 == null || !response2.equals(RESPONSE_OK)) {
                throw new IOException("Could not read response for status info '" + status + "'. " +
                    "Expected '" + RESPONSE_OK + "', but got '" + response2 + "'.");
            }
            return result;
        });
    }

    /**
     * Check if a new event string arrived from the Tor control port instance,
     * which is ready to be processed
     *
     * @return
     */
    private boolean isEventAvailable() {
        try {
            return telnetConnection.isLineAvailableToRead();
        } catch (IOException e) {
            log.warn("Something went wrong when asking if there are new Tor control port events available.", e);
        }
        return false;
    }

    /**
     * Check for the subscribed STATUS_CLIENT events sent by the Tor control port.
     * If we see : "NOTICE CIRCUIT_ESTABLISHED" its OK, furthermore if we do not see this event in a certain time,
     * AND get a "NOTICE NOT_ENOUGH_DIR_INFO" we can assume, that the current Tor configuration (e.g. of selected ExitNodes) is not able
     * to establish circuits or in other words a proper connection through the Tor network;
     * <p>
     * Some example event strings which the Tor control port is sending:
     * <p>
     * could not build circuit (e.g. cause there is no known exit node with the selected geoip?!):
     * 650 STATUS_CLIENT NOTICE NOT_ENOUGH_DIR_INFO
     * <p>
     * was able to build circuit:
     * 650 STATUS_CLIENT NOTICE ENOUGH_DIR_INFO
     * 650 STATUS_CLIENT NOTICE CIRCUIT_ESTABLISHED
     *
     * @return
     */
    private void processStatusClientEvents() {
        try {
            List<String> statusClientEventStrings = telnetConnection.readAvailableLines();
            for (String statusClientEventString : statusClientEventStrings) {
                Matcher matcher = statusClientEventPattern.matcher(statusClientEventString);

                if (matcher.matches()) {//we found an event we are rested in
                    String eventType = matcher.group(1);//get last part (=eventtype) of event string
                    log.info("Received status_client event: " + eventType);
                    switch (eventType) {
                        case TOR_EVENT_NOT_ENOUGH_DIR_INFO:
                            status = TorStatus.NOT_ENOUGH_DIR_INFO;
                            break;
                        case TOR_EVENT_CIRCUIT_ESTABLISHED:
                            status = TorStatus.CIRCUIT_ESTABLISHED;
                            break;
                        case TOR_EVENT_ENOUGH_DIR_INFO:
                            status = TorStatus.READY;
                            break;
                        default:
                            log.debug("Ignoring event type {}", eventType);
                    }
                } else {
                    log.debug("Ignoring event: {}", statusClientEventString);
                }
            }

        } catch (IOException e) {
            log.warn("Error while trying to read events from Tor control port.", e);
        }
    }

    /**
     * Check the bootstrap phase and assume that Tor is working, if the percent or state that the bootstrap phase is in is 100%;
     *
     * @return
     */
    private boolean isBootstrapPhaseComplete() {
        String result = getInfo(STATUS_BOOTSTRAP_PHASE);

        log.debug("Tor bootstrap phase: {}", result);

        if (result != null) {
            //start extracting the state/percentage of the bootstrap phase
            String[] parts = result.split("PROGRESS=");
            if (parts != null && parts.length >= 2) {
                String info = parts[1];
                log.debug("Interesting part of Tor bootstrap info: {}", info);

                String percentage = info.split(" ")[0];
                log.debug("Tor bootstrap phase: {}%", percentage);

                if (percentage != null && "100".equals(percentage)) {
                    log.debug("Tor instance was bootstrapped successfully.");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Use this method to get the latest connection state of the Tor client.
     *
     * @return
     */
    public boolean isConnectedToTorNetwork() {
        return status == TorStatus.CIRCUIT_ESTABLISHED || status == TorStatus.READY;
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
        return tryWithReconnectionAttempt(() -> {
            telnetConnection.writeLine(command);
            String result = telnetConnection.readLine();
            if (result == null) {
                throw new IOException("Could not read response for command: " + command);
            }
            return result.equals(RESPONSE_OK);
        });
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
    public void closeTorTelnetConnection() {
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
