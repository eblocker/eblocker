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

/**
 * Controls the local DHCP server
 */
public interface DhcpServer {
    /**
     * Starts the DHCP server and ensures that it is automatically restarted
     * after a reboot.
     */
    void enable(boolean start);
	
	/**
	 * Stops the DHCP server and ensures that it is not started after a reboot.
	 */
	public void disable();
	
	/**
	 * Updates the DHCP server's configuration. This might restart the server.
	 * @param configuration the configuration to apply
	 */
	public void setConfiguration(DhcpServerConfiguration configuration);
}
