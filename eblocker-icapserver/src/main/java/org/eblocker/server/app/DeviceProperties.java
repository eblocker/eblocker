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
package org.eblocker.server.app;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * injectable singleton, which reads a properties file (default: <code>/etc/eblocker-device.properties</code>
 * and provides these properties via getter methods.
 * <p>
 * The purpose of these properties: They should contain information about relevant features that depend on the actual
 * hardware, on which the eBlocker Server is running.
 * <p>
 * E.g.:
 * <ul>
 *     <li>Is Wifi available?</li>
 *     <li>Is a HW serial number available?</li>
 *     <li>Name of the board.</li>
 *     <li>...</li>
 * </ul>
 */
@Singleton
public class DeviceProperties {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceProperties.class);

    private static final String DEVICE_PROP_ARCH_KEY = "device.arch";
    private static final String DEVICE_PROP_ARCH_DEFAULT = "(unknown)"; // "arm"

    private static final String DEVICE_PROP_BOARD_KEY = "device.board";
    private static final String DEVICE_PROP_BOARD_DEFAULT = "(unknown)"; // "Banana PI M2+"

    private static final String DEVICE_PROP_CASE_KEY = "device.case";
    private static final String DEVICE_PROP_CASE_DEFAULT = "(unknown)"; // "Cube 1.0"

    private static final String DEVICE_PROP_HAS_SERIALNUMBER_KEY = "device.serialnumber.available";
    private static final String DEVICE_PROP_HAS_SERIALNUMBER_DEFAULT = "false"; // MUST be false by default, because "old" eBlockers do not have a serial number

    private static final String DEVICE_PROP_SERIALNUMBER_PATTERN_KEY = "device.serialnumber.pattern";
    private static final String DEVICE_PROP_SERIALNUMBER_PATTERN_DEFAULT = "SN(\\d){8}";

    private static final String DEVICE_PROP_SERIALNUMBER_EXAMPLE_KEY = "device.serialnumber.example";
    private static final String DEVICE_PROP_SERIALNUMBER_EXAMPLE_DEFAULT = "SN12345678"; // not used, if device.serialnumber.available=false

    private static final String DEVICE_PROP_HAS_WIFI_KEY = "device.wifi.available"; // not used, if device.serialnumber.available=false
    private static final String DEVICE_PROP_HAS_WIFI_DEFAULT = "false"; // MUST be false by default, because "old" eBlockers do not have Wifi

    private static final String DEVICE_PROP_HAS_RGB_LED_KEY = "device.led.rgb.available";
    private static final String DEVICE_PROP_HAS_RGB_LED_DEFAULT = "false"; // MUST be false by default, because "old" eBlockers do not have an RGB LED

    private final Properties properties;

    private Pattern serialNumberPattern = null;

    @Inject
    public DeviceProperties(@Named("deviceProperties") String devicePropertiesPath) {
        properties = new Properties();
        try {
            InputStream resource = ResourceHandler.getInputStream(new SimpleResource(devicePropertiesPath));
            if (resource == null) {
                LOG.info("Cannot load device properties from {}, using default values", devicePropertiesPath);
            } else {
                properties.load(resource);
            }
        } catch (EblockerException e) {
            LOG.info("Cannot load device properties from {}, using default values", devicePropertiesPath, e);
        } catch (IOException e) {
            LOG.warn("Cannot load device properties from {}, using default values", devicePropertiesPath, e);
        }

        if (getSerialNumberPattern() != null) {
            serialNumberPattern = Pattern.compile(getSerialNumberPattern(), Pattern.CASE_INSENSITIVE);
        }
    }

    public boolean isSerialNumberAvailable() {
        String value = properties.getProperty(DEVICE_PROP_HAS_SERIALNUMBER_KEY, DEVICE_PROP_HAS_SERIALNUMBER_DEFAULT);
        return Boolean.valueOf(value);
    }

    public boolean isWifiAvailable() {
        String value = properties.getProperty(DEVICE_PROP_HAS_WIFI_KEY, DEVICE_PROP_HAS_WIFI_DEFAULT);
        return Boolean.valueOf(value);
    }

    public boolean isRgbLedAvailable() {
        String value = properties.getProperty(DEVICE_PROP_HAS_RGB_LED_KEY, DEVICE_PROP_HAS_RGB_LED_DEFAULT);
        return Boolean.valueOf(value);
    }

    public String getArchitecture() {
        return properties.getProperty(DEVICE_PROP_ARCH_KEY, DEVICE_PROP_ARCH_DEFAULT);
    }

    public String getBoard() {
        return properties.getProperty(DEVICE_PROP_BOARD_KEY, DEVICE_PROP_BOARD_DEFAULT);
    }

    public String getCase() {
        return properties.getProperty(DEVICE_PROP_CASE_KEY, DEVICE_PROP_CASE_DEFAULT);
    }

    public String getSerialNumberPattern() {
        return properties.getProperty(DEVICE_PROP_SERIALNUMBER_PATTERN_KEY, DEVICE_PROP_SERIALNUMBER_PATTERN_DEFAULT);
    }

    public boolean isSerialNumberMatching(String serialNumber) {
        if (serialNumberPattern == null) {
            return true;
        }
        return serialNumberPattern.matcher(serialNumber).matches();
    }

    public String getSerialnumberExample() {
        return properties.getProperty(DEVICE_PROP_SERIALNUMBER_EXAMPLE_KEY, DEVICE_PROP_SERIALNUMBER_EXAMPLE_DEFAULT);
    }
}
