/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.http.backup;

import com.google.inject.Inject;
import org.eblocker.server.http.service.DnsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Backs up and restores DNS firewall settings.
 */
public class DnsBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DnsBackupProvider.class);
    public static final String DNS_ENTRY = "eblocker-config/dns.json";

    private final DnsService dnsService;

    @Inject
    public DnsBackupProvider(DnsService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        DnsBackup backup = createBackup();
        byte[] backupBytes = objectMapper.writeValueAsBytes(backup);
        writeNextEntry(outputStream, DNS_ENTRY, backupBytes);
    }

    private DnsBackup createBackup() {
        DnsBackup backup = new DnsBackup();
        backup.setEnabled(dnsService.isEnabled());
        backup.setDnsResolvers(dnsService.getDnsResolvers());
        backup.setLocalDnsRecords(dnsService.getLocalDnsRecords());
        return backup;
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, false);
    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, true);
    }

    private void importConfiguration(JarInputStream inputStream, int schemaVersion, boolean dryRun) throws IOException {
        getNextEntry(inputStream, DNS_ENTRY);

        DnsBackup backup = objectMapper.readValue(inputStream, DnsBackup.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        if (!dryRun) {
            restoreBackup(backup);
        }
    }

    private void restoreBackup(DnsBackup backup) {
        dnsService.setStatus(backup.isEnabled());
        dnsService.setDnsResolvers(backup.getDnsResolvers());
        dnsService.setLocalDnsRecords(backup.getLocalDnsRecords());
    }
}
