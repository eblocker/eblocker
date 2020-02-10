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
package org.eblocker.server.common.registration;

import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.server.common.system.CommandRunner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class DeviceRegistrationLicenseStateImpl implements DeviceRegistrationLicenseState {
    private String curlCommand;
    private String aptUrl;
    private String caCertFullPath;
    private CommandRunner commandRunner;
    private final EblockerResource licenseKey;
    private final EblockerResource licenseCert;
    private static final String CURL_MAX_TIME = "3"; //3 seconds maximum for the whole curl operation

    @Inject
    public DeviceRegistrationLicenseStateImpl(
            CommandRunner commandRunner,
            @Named("registration.curl.command") String curlCommand,
            @Named("registration.license.key") String licenseKey,
            @Named("registration.license.cert") String licenseCert,
            @Named("baseurl.apt") String aptUrlDomain,
            @Named("registration.apt.url.path") String aptUrlPath,
            @Named("registration.truststore.copy") String caCertPath
            ) {
        this.commandRunner = commandRunner;
        this.curlCommand = curlCommand;
        this.licenseKey = new SimpleResource(licenseKey);
        this.licenseCert = new SimpleResource(licenseCert);
        this.aptUrl = aptUrlDomain + aptUrlPath; // Add URL suffix because directory listing is forbidden and will result in 403 even if certificate is valid. File named by suffix doesn't need to exists on the web server
        this.caCertFullPath = caCertPath;
    }

    /**
     * Checks whether certificate is rejected by the HTTP server in a defensive way, i.e. if the server doesn't reply code 403 explicitly within 3 seconds, RegistrationState.OK is assumed.
     * That could lead to a valid registration assumption for many reasons, e.g. when no internet connection is available and no exception has been thrown.
     * @return Returns RegistrationState.INVALID if certificate is rejected due to expiration or revocation.
     */
    @Override
    public RegistrationState checkCertificate() {
        try {
            String httpResult = commandRunner.runCommandWithOutput(
                    curlCommand,
                    "--silent",
                    "-o /dev/null",
                    "-w %{http_code}",
                    "--cert", licenseCert.getPath(),
                    "--key", licenseKey.getPath(),
                    "--cacert", caCertFullPath,
                    "--max-time", CURL_MAX_TIME,
                    aptUrl).trim();

            if (Integer.parseInt(httpResult) == 403) {
                return RegistrationState.INVALID;
            }
        }
        catch (Exception e) {
            throw new EblockerException("Unknown response. Revokation state check failed.", e);
        }

        return RegistrationState.OK;
    }
}
