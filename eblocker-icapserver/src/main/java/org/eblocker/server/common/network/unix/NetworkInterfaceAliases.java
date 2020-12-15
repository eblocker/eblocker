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
package org.eblocker.server.common.network.unix;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class can be used to dynamically add or remove interface aliases.
 */
public class NetworkInterfaceAliases {
    private static final Logger logger = LoggerFactory.getLogger(NetworkInterfaceAliases.class);

    private final String interfaceName;
    private final int aliasMin;
    private final int aliasMax;
    private final String addAliasScript;
    private final String removeAliasScript;

    private ScriptRunner scriptRunner;

    private Queue<Integer> availableAliases = new LinkedList<>();
    private Map<String, String> assignedIps = new HashMap<>();

    @Inject
    public NetworkInterfaceAliases(@Named("network.interface.name") String interfaceName,
                                   @Named("network.alias.min") int aliasMin,
                                   @Named("network.alias.max") int aliasMax,
                                   @Named("network.alias.script.add") String addAliasScript,
                                   @Named("network.alias.script.remove") String removeAliasScript,
                                   ScriptRunner scriptRunner) {
        this.interfaceName = interfaceName;
        this.aliasMin = aliasMin;
        this.aliasMax = aliasMax;
        this.addAliasScript = addAliasScript;
        this.removeAliasScript = removeAliasScript;
        this.scriptRunner = scriptRunner;

        removeStaleAliases();
        setupAvailableAliases(aliasMin, aliasMax);
    }

    /**
     * Add an ip with netmask the primary interface.
     *
     * @param ip      ip to assign
     * @param netmask netmask to assin
     * @return assigned alias
     */
    public synchronized String add(String ip, String netmask) {
        Integer aliasNumber = availableAliases.poll();
        if (aliasNumber == null) {
            logger.error("no more aliases available, failed to create one with ip: {} and netmask: {}", ip, netmask);
            return null;
        }

        String alias = interfaceName + ":" + aliasNumber;

        try {
            scriptRunner.runScript(addAliasScript, alias, ip, netmask);
            assignedIps.put(alias, ip);
            logger.info("assigned ip: {} and netmask: {} to alias {}", ip, netmask, alias);
            return alias;
        } catch (IOException e) {
            logger.error("failed to create alias {} with ip: {} and netmask: {}", alias, ip, netmask, e);
            availableAliases.add(aliasNumber);
            return null;
        } catch (InterruptedException e) {
            logger.error("Adding NetworkInterfaceAliases interrupted", e);
            availableAliases.add(aliasNumber);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Removes an assigned alias from the primary interface
     *
     * @param alias to remove
     */
    public synchronized void remove(String alias) {
        if (!assignedIps.containsKey(alias)) {
            logger.warn("trying to remove unassigned alias {}!", alias);
            return;
        }

        try {
            scriptRunner.runScript(removeAliasScript, alias);
            assignedIps.remove(alias);
            int n = Integer.parseInt(alias.substring(interfaceName.length() + 1));
            availableAliases.add(n);
            logger.info("removed alias for {}", alias);
        } catch (IOException e) {
            logger.error("failed to remove alias {}", alias, e);
        } catch (InterruptedException e) {
            logger.error("Interrupt exception while remove alias", e);
            Thread.currentThread().interrupt();
        }
    }

    public synchronized Collection<String> getAliasedIps() {
        return new HashSet<>(assignedIps.values());
    }

    private void removeStaleAliases() {
        try {
            NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
            if (networkInterface == null) {
                logger.warn("Cannot access network interface {}", interfaceName);
                return;
            }
            for (NetworkInterface subInterface : Collections.list(networkInterface.getSubInterfaces())) {
                String alias = subInterface.getName();
                if (isManagedAlias(alias)) {
                    logger.info("removing stale alias {}", alias);
                    try {
                        scriptRunner.runScript(removeAliasScript, alias);
                    } catch (IOException e) {
                        logger.error("failed to remove stale alias {}", alias, e);
                    } catch (InterruptedException e) {
                        logger.error("Interrupt exception while removeStateAliases", e);
                        Thread.currentThread().interrupt();
                    }

                } else {
                    logger.debug("ignoring non-managed alias {}", alias);
                }
            }
        } catch (SocketException e) {
            logger.error("failed to get interface for alias cleaning", e);
        }
    }

    private boolean isManagedAlias(String alias) {
        Pattern pattern = Pattern.compile(interfaceName + ":(\\d+)");
        Matcher matcher = pattern.matcher(alias);
        if (!matcher.find()) {
            return false;
        }

        int n = Integer.parseInt(matcher.group(1));
        return n >= aliasMin && n <= aliasMax;
    }

    private void setupAvailableAliases(int min, int max) {
        logger.info("{} available aliases ({} - {})", (1 + max - min), min, max);
        for (int i = aliasMin; i <= aliasMax; ++i) {
            availableAliases.add(i);
        }
    }
}
