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

import java.io.IOException;

import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.network.TelnetConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eblocker.server.app.DeviceProperties;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Communicates with the service eblocker-led to control the RGB LED.
 */
@Singleton
public class StatusLedService {
    private static final Logger log = LoggerFactory.getLogger(StatusLedService.class);

    private DeviceProperties deviceProperties;
    private final TelnetConnection connection;
    private final String host;
    private final Integer port;

    @Inject
    public StatusLedService(DeviceProperties deviceProperties,
                            @Named("led.service.host") String ledControllerHost,
                            @Named("led.service.port") Integer ledControllerPort,
                            TelnetConnection telnetConnection,
                            SystemStatusService systemStatusService) {
        this.deviceProperties = deviceProperties;
        this.host = ledControllerHost;
        this.port = ledControllerPort;
        this.connection = telnetConnection;
        systemStatusService.addListener((ExecutionState newState) -> {
            setStatus(newState);
        });
    }

    public void setStatus(ExecutionState status) {
        if (isHardwareAvailable()) {
            try {
                String response = sendCommand("status=" + status.name().toLowerCase());
                if (response == null || !response.equals("OK")) {
                    log.error("Expected response OK, got '{}' setting LED status to {}", response, status);
                }
            } catch (IOException e) {
                log.error("Could not set RGB LED status to {}", status, e);
            }
        }
    }

    public void setBrightness(float factor) {
        if (isHardwareAvailable()) {
            try {
                String response = sendCommand("brightness=" + factor);
                if (response == null || !response.equals("OK")) {
                    log.error("Expected response OK, got '{}' setting LED brightness to {}", response, factor);
                }
            } catch (IOException e) {
                log.error("Could not set RGB LED brightness to {}", factor, e);
            }
        }
    }

    public float getBrightness() {
        if (isHardwareAvailable()) {
            try {
                String response = sendCommand("brightness");
                try {
                    return Float.parseFloat(response);
                } catch (NullPointerException | NumberFormatException e) {
                    log.error("Could not parse return value for LED brightness ({}) as float", response);
                }
            } catch (IOException e) {
                log.error("Could not get RGB LED brightness", e);
            }
        }
        return 1.0f;
    }

    public boolean isHardwareAvailable() {
        return deviceProperties.isRgbLedAvailable();
    }

    private String sendCommand(String command) throws IOException {
        connection.connect(host, port);
        connection.writeLine(command);
        String response = connection.readLine();
        connection.close();
        return response;
    }
}
