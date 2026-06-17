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
package org.eblocker.server.common.squid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.Environment;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.vpn.VpnClientState;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.network.Ip6PrefixMonitor;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.service.FeatureService;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.common.squid.acl.ConfigurableDeviceFilterAcl;
import org.eblocker.server.common.squid.acl.ConfigurableDeviceFilterAclFactory;
import org.eblocker.server.common.squid.acl.SquidAcl;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.DeviceService.DeviceChangeListener;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is able to rewrite ACL files which are used in the squid config file; In addition it is able to tell squid, that the configuration changed and
 * a reload is neccessary.
 */
@Singleton
@SubSystemService(value = SubSystem.HTTPS_SERVER, initPriority = 150)
public class SquidConfigController {
    private static final Logger log = LoggerFactory.getLogger(SquidConfigController.class);

    private final String squidConfigFilePath; //local squid config output file -> copy this in squid_reconfigure to real squid config path
    private final String squidClearCertCacheScript;

    private final ScriptRunner scriptRunner;
    private final SquidReloadingService reloadingService;
    private final DataSource dataSource;
    private final NetworkInterfaceWrapper networkInterface;
    private final SslService sslService;
    private final JsonWebTokenHandler tokenHandler;
    private final ConfigurableDeviceFilterAclFactory squidAclFactory;
    private final Ip6PrefixMonitor prefixMonitor;
    private final FeatureService featureService;

    private final SimpleResource mimeTypesAcl;//list of MIME types to send to this Icapserver (Squid has to forward/hand files of these MIMEtypes to the ICAP server...)
    private final SimpleResource squidConfigTemplateFile; //template part of squid config
    private final SimpleResource squidConfigStaticFile; //static part of squid config
    private final SimpleResource squidConfigSslExclusiveFile; // options only set in case ssl is enabled
    private final SimpleResource squidConfigNoSslExclusiveFile; // options only set in case ssl is disabled

    private final SquidAcl disabledClientsAcl;
    private final ConfigurableDeviceFilterAcl filteredClientsAcl; // devices with domain filtering enabled
    private final SquidAcl mobileClientsAcl;
    private final SquidAcl mobileClientsPrivateNetworkAccessAcl;
    private final SquidAcl sslClientsAcl;
    private final SquidAcl torClientsAcl;
    private final Map<Integer, ConfigurableDeviceFilterAcl> vpnAcls = new HashMap<>();
    private final String vpnAclDirectoryPath;
    private final String sslKeyFilePath;
    private final String sslCertFilePath;
    private final String squidWorkers;
    private final String controlBarHostName;
    private final String controlBarHostFallbackIp;
    private final String cacheLog;
    private final Environment environment;

    private final List<String> defaultMimeTypes = List.of("text/html", "text/xhtml");
    private final List<String> javascriptMimeTypes = List.of("text/javascript", "application/javascript", "application/x-javascript", "text/ecmascript", "application/ecmascript");
    private boolean enableJavascriptFiltering = false;


    @Inject
    public SquidConfigController(@Named("squid.acl.ssl.clients") SquidAcl sslClientsAcl,
                                 @Named("squid.acl.tor.clients") SquidAcl torClientsAcl,
                                 @Named("squid.acl.disabled.clients") SquidAcl disabledClientsAcl,
                                 @Named("squid.acl.filtered.clients") ConfigurableDeviceFilterAcl filteredClientsAcl,
                                 @Named("squid.acl.mobile.clients") SquidAcl mobileClientsAcl,
                                 @Named("squid.acl.mobile.clients.private.network.access") SquidAcl mobileClientsPrivateNetworkAccessAcl,
                                 @Named("squid.vpn.acl.directory.path") String vpnAclDirectoryPath,
                                 @Named("squid.mime.types.acl.file.path") String mimeAclFilePath,
                                 @Named("squid.xForward.domains.acl.file.path") String xForwardDomainsAclFilePath,
                                 @Named("squid.xForward.ips.acl.file.path") String xForwardIpsAclFilePath,
                                 @Named("squid.xForward.ips") String xForwardIps,
                                 @Named("squid.clear.cert.cache.command") String squidClearCertCacheScript,
                                 @Named("squid.config.template.file.path") String confTemplateFilePath,
                                 @Named("squid.config.static.file.path") String confStaticFilePath,
                                 @Named("squid.config.ssl.exclusive.file.path") String squidConfigSslExclusiveFile,
                                 @Named("squid.config.noSsl.exclusive.file.path") String squidConfigNoSslExclusiveFile,
                                 @Named("squid.config.file.path") String confFilePath,
                                 @Named("squid.cache.log") String cacheLog,
                                 @Named("squid.ssl.ca.key") String sslKeyFilePath,
                                 @Named("squid.ssl.ca.cert") String sslCertFilePath,
                                 @Named("squid.workers") String squidWorkers,
                                 @Named("network.control.bar.host.name") String controlBarHostName,
                                 @Named("network.control.bar.host.fallback.ip") String controlBarHostFallbackIp,
                                 @Named("dns.server.default.local.names") String dnsLocalNames,
                                 ScriptRunner scriptRunner,
                                 SquidReloadingService reloadingService,
                                 DataSource dataSource,
                                 SslService sslService,
                                 NetworkInterfaceWrapper networkInterface,
                                 JsonWebTokenHandler tokenHandler,
                                 NetworkServices networkServices,
                                 DeviceService deviceService,
                                 ConfigurableDeviceFilterAclFactory squidAclFactory,
                                 Environment environment,
                                 Ip6PrefixMonitor prefixMonitor,
                                 FeatureServiceSubscriber featureServiceSubscriber) {

        this.squidClearCertCacheScript = squidClearCertCacheScript;
        this.squidConfigFilePath = confFilePath;
        this.cacheLog = cacheLog;

        this.scriptRunner = scriptRunner;
        this.reloadingService = reloadingService;
        this.dataSource = dataSource;
        this.networkInterface = networkInterface;
        this.sslService = sslService;
        this.tokenHandler = tokenHandler;
        this.squidAclFactory = squidAclFactory;

        this.squidConfigStaticFile = new SimpleResource(confStaticFilePath);
        this.squidConfigTemplateFile = new SimpleResource(confTemplateFilePath);
        this.squidConfigSslExclusiveFile = new SimpleResource(squidConfigSslExclusiveFile);
        this.squidConfigNoSslExclusiveFile = new SimpleResource(squidConfigNoSslExclusiveFile);
        this.mimeTypesAcl = new SimpleResource(mimeAclFilePath);

        this.vpnAclDirectoryPath = vpnAclDirectoryPath;

        this.sslKeyFilePath = sslKeyFilePath;
        this.sslCertFilePath = sslCertFilePath;
        this.squidWorkers = squidWorkers;
        this.controlBarHostName = controlBarHostName;
        this.controlBarHostFallbackIp = controlBarHostFallbackIp;

        this.disabledClientsAcl = disabledClientsAcl;
        this.filteredClientsAcl = filteredClientsAcl;
        this.mobileClientsAcl = mobileClientsAcl;
        this.mobileClientsPrivateNetworkAccessAcl = mobileClientsPrivateNetworkAccessAcl;
        this.sslClientsAcl = sslClientsAcl;
        this.torClientsAcl = torClientsAcl;

        this.environment = environment;

        this.prefixMonitor = prefixMonitor;
        this.featureService = featureServiceSubscriber;

        if (!ResourceHandler.exists(squidConfigStaticFile)) {
            log.error("Squid static config filepart does not exist at {}", confStaticFilePath);
        }

        if (!ResourceHandler.exists(squidConfigTemplateFile)) {
            log.error("Squid template config filepart does not exist at {}", confTemplateFilePath);
        }

        if (!squidMimeACLFileExists()) {
            log.info("Squid mime types acl file does not exist at {}. Creating it.", mimeAclFilePath);
            ResourceHandler.create(this.mimeTypesAcl);
        }

        initMimeTypesACL();
        featureServiceSubscriber.addListener(this::updateMimeTypes);

        initXForwardDomainsAcl(dnsLocalNames, xForwardDomainsAclFilePath);
        initXForwardIpsAcl(xForwardIps, xForwardIpsAclFilePath);

        // register callback to trigger reload on name server changes
        networkServices.addListener(l -> reloadingService.tellSquidToReloadConfig());

        // register callback to trigger on device changes
        deviceService.addListener(new DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                updateAcls();
            }

            @Override
            public void onDelete(Device device) {
                // Nothing to do here
            }

            @Override
            public void onReset(Device device) {
                // Nothing to do here
            }
        });

        // initial config will be written after ssl has been initialized
        // register callback for ssl ca changes
        sslService.addListener(new SslService.BaseStateListener() {
            @Override
            public void onInit(boolean sslEnabled) {
                writeKeys();
                updateSquidConfig();
                updateAcls();
            }

            @Override
            public void onCaChange() {
                writeKeys();
                clearCertificateCache();
                reloadingService.tellSquidToReloadConfig();
            }

            @Override
            public void onEnable() {
                updateSquidConfig();
            }

            @Override
            public void onDisable() {
                updateSquidConfig();
            }
        });
    }

    @SubSystemInit
    public void init() {
        // this requires the SslService to be initialized:
        prefixMonitor.addPrefixChangeListener(this::updateSquidConfig);
    }

    /**
     * Check if the mime type acl file for the squid config exists
     *
     * @return
     */
    private boolean squidMimeACLFileExists() {
        return ResourceHandler.exists(mimeTypesAcl);
    }

    /**
     * Rewrite the list of MAC addresses for filtered devices. These devices
     * will be included in domain filtering.
     */
    public void updateDomainFilteredDevices(Set<Device> domainFilteredDevices) {
        updateAcl(filteredClientsAcl, domainFilteredDevices);
    }

    public void updateVpnDevicesAcl(int id, Set<Device> devices) {
        synchronized (vpnAcls) {
            ConfigurableDeviceFilterAcl acl = vpnAcls.get(id);
            if (acl == null) {
                acl = squidAclFactory.create(vpnAclDirectoryPath + "/vpn-" + id + ".acl");
                vpnAcls.put(id, acl);
            }
            updateAcl(acl, devices);
        }
    }

    private synchronized void updateAcl(ConfigurableDeviceFilterAcl acl, Set<Device> devices) {
        acl.setDevices(devices);
        acl.update();
        reloadingService.tellSquidToReloadConfig();
    }

    /**
     * Prepare the MimeTypes that Squid should send to the Icapserver
     */
    private void initMimeTypesACL() {
        enableJavascriptFiltering = featureService.getWebRTCBlockingState();
        writeMimeTypesAcl();
    }

    @Nonnull
    private List<String> getFilteredMimeTypes() {
        List<String> filteredMimeTypes = new ArrayList<>(8);

        // always add the default Mime types to send to the ICAP server (text/html and text/xhtml)
        filteredMimeTypes.addAll(defaultMimeTypes);

        // if WebRTC is enabled we have to also send JavaScripts to the ICAP server
        if (enableJavascriptFiltering) {
            filteredMimeTypes.addAll(javascriptMimeTypes);
        }
        return filteredMimeTypes;
    }

    /**
     * Write a set of Mime type strings to the squid acl file
     */
    private synchronized void writeMimeTypesAcl() {
        ResourceHandler.replaceContent(mimeTypesAcl, getFilteredMimeTypes());
    }

    /**
     * Enable/disable providing of JavaScript files to the ICAP server through Squid
     */
    public void updateMimeTypes() {
        boolean enabled = featureService.getWebRTCBlockingState();
        if (enableJavascriptFiltering != enabled) { //state changed -> apply this setting
            enableJavascriptFiltering = enabled;
            writeMimeTypesAcl();
            reloadingService.tellSquidToReloadConfig();
        }
    }

    // X-Forward Acls ------------------------------------------------------
    private void initXForwardDomainsAcl(String dnsLocalNames, String domainsAclFilePath) {
        List<String> localNames = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(dnsLocalNames);

        List<String> domains = new ArrayList<>();
        domains.add(controlBarHostName);
        domains.addAll(localNames);

        EblockerResource domainsAcl = new SimpleResource(domainsAclFilePath);
        ResourceHandler.replaceContent(domainsAcl, domains);
    }

    private void initXForwardIpsAcl(String xForwardIps, String ipsAclFilePath) {
        List<String> configuredIps = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(xForwardIps);

        List<String> ips = new ArrayList<>();
        ips.add(controlBarHostFallbackIp);
        ips.addAll(configuredIps);

        EblockerResource domainsAcl = new SimpleResource(ipsAclFilePath);
        ResourceHandler.replaceContent(domainsAcl, ips);
    }

    /**
     * This function will reconstruct the squid.conf and reload the squid.
     *
     * @return
     */
    public void updateSquidConfig() {
        String squidConfigContent = constructSquidConfigString(sslService.isSslEnabled(), dataSource.getAll(VpnClientState.class));
        if (writeSquidConfig(squidConfigContent)) {
            reloadingService.tellSquidToReloadConfig();
        }
    }

    /**
     * Pure writing of content string to local squid config file in /opt/eblocker....
     * Note: New config will only be applied when tellSquidToReconfigure() is called after this method.
     *
     * @param content the content for the squid.conf file
     */
    private synchronized boolean writeSquidConfig(String content) {
        if (content == null)
            return false;

        //overwrite local squid config file with finished merged file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(squidConfigFilePath))) {
            writer.write(content);
        } catch (IOException e) {
            log.error("Overriding the Squid config file {} did not work", squidConfigFilePath, e);
            return false;
        }

        return true;
    }

    /**
     * Construct the squid main config content string by concatenating the static config part, the dynamic part and the ssl config part
     * Take the template part of the squid config and expand it depending on whether SSL support is enabled or not,
     * and concat it to the static part of the config and then write it.
     * It will be copied when running the squid reconfigure script
     *
     * @param sslReady whether squid should do SSL bumping or not (should only be true, if the root CA and private key exist already !!! -> otherwise Squid will fail and terminate!!!)
     * @return
     */
    private String constructSquidConfigString(boolean sslReady, Collection<VpnClientState> vpnClients) {
        //template exists?
        if (!ResourceHandler.exists(squidConfigTemplateFile)) {
            log.error("Squid config template file can not be found here {}", squidConfigTemplateFile.getPath());
            return null;
        }
        if (!ResourceHandler.exists(squidConfigStaticFile)) {
            log.error("Squid config static file part can not be found here {}", squidConfigStaticFile.getPath());
            return null;
        }

        log.info("Building squid config content...");

        StringBuilder configContent = new StringBuilder();

        String sslConfPart = ResourceHandler.load(squidConfigTemplateFile);
        String sslExclusivePart = ResourceHandler.load(squidConfigSslExclusiveFile);
        String nonSslExclusivePart = ResourceHandler.load(squidConfigNoSslExclusiveFile);
        String confStatic = ResourceHandler.load(squidConfigStaticFile);
        String confDynamic = createDynamicOptions(vpnClients);

        configContent.append(confStatic);
        configContent.append(confDynamic);

        if (sslReady) { //just if SSL is enabled and the configurations are created already
            log.info("Adding SSL Support in squid config...");
            configContent.append(sslConfPart);
            if (!environment.isServer()) {
                configContent.append(sslExclusivePart);
            }
        } else {
            configContent.append(nonSslExclusivePart);
        }

        return configContent.toString();
    }

    private String createDynamicOptions(Collection<VpnClientState> vpnClients) {
        StringBuilder sb = new StringBuilder();

        sb.append(createLogOptions());
        sb.append(createErrHtmlOption());
        sb.append(createWorkersOption());
        sb.append(createIp6Options());
        sb.append(createVpnOptions(vpnClients));

        return sb.toString();
    }

    private String createLogOptions() {
        String options = "ALL,0";
        String file = "/dev/null";

        if (dataSource.getSslRecordErrors()) {
            options = "83,1";
            file = cacheLog;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("cache_log %s\n", file));
        sb.append(String.format("debug_options %s\n", options));
        return sb.toString();
    }

    private String createVpnOptions(Collection<VpnClientState> vpnClients) {
        if (vpnClients != null && !vpnClients.isEmpty()) {

            StringBuilder squidConfigContent = new StringBuilder();
            //now start adding VPN link local address substitution part
            squidConfigContent.append("\n");
            squidConfigContent.append("#------------------------------------------------------------------------------\n");
            squidConfigContent.append("# VPN clients support\n");
            squidConfigContent.append("#------------------------------------------------------------------------------\n");

            for (VpnClientState client : vpnClients) {
                if (client.getState() == VpnClientState.State.ACTIVE) {
                    String line1 = String.format("acl outbound_vpn_%d src \"/etc/squid/vpn-%d.acl\"", client.getId(), client.getId());
                    String line2 = String.format("tcp_outgoing_mark 0x%x outbound_vpn_%d !disabledClients", client.getRoute(), client.getId());

                    squidConfigContent.append("# VPN client " + client.getId() + "\n");
                    squidConfigContent.append(line1);
                    squidConfigContent.append("\n");
                    squidConfigContent.append(line2);
                    squidConfigContent.append("\n");
                }
            }
            squidConfigContent.append("\n");
            return squidConfigContent.toString();
        }

        return "";
    }

    // This puts the current IP address into the configuration directive "err_html_text".
    // In Squid's error templates, we can then use the placeholder %L to get the IP address.
    private String createErrHtmlOption() {
        try {
            Map<String, Object> dynamicConfig = new LinkedHashMap<>(); // must ensure order to get exact match in unit test :(
            if (dataSource.getWireGuardMobileServerState()) {
                dynamicConfig.put("ip", controlBarHostName);
            } else if (networkInterface.getFirstIPv4Address() != null) {
                dynamicConfig.put("ip", networkInterface.getFirstIPv4Address().toString());
            }
            dynamicConfig.put("token", tokenHandler.generateSquidToken().getToken());
            return "err_html_text " + new ObjectMapper().writeValueAsString(dynamicConfig) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize dynamic config", e);
        }
    }

    /**
     * Set number of squid worker processes according to the configuration parameter "squid.workers".
     *
     * If it is set to "auto":
     * Use a second worker if the JVM can use more than 500 MB.
     *
     * @return configuration snippet for Squid
     */
    private String createWorkersOption() {
        StringBuilder cfg = new StringBuilder("workers ");
        if (squidWorkers.equalsIgnoreCase("auto")) {
            if (Runtime.getRuntime().maxMemory() > 500L*1024L*1024L) {
                cfg.append(2);
            } else {
                cfg.append(1);
            }
        } else {
            cfg.append(squidWorkers);
        }
        cfg.append("\n");
        return cfg.toString();
    }

    /**
     * Allow access from and to IPv6 networks
     *
     * @return Squid configuration snippet
     */
    private String createIp6Options() {
        return prefixMonitor.getCurrentPrefixes().stream()
                .sorted()
                .flatMap(ip -> Stream.of(
                        "acl localnet src " + ip + "\n",
                        "acl localnetDst dst " + ip + "\n"))
                .collect(Collectors.joining());
    }

    /* updates all acls and reload squid in case of changes */
    private void updateAcls() {
        boolean update = torClientsAcl.update();
        update |= sslClientsAcl.update();
        update |= filteredClientsAcl.update();
        update |= disabledClientsAcl.update();
        update |= mobileClientsAcl.update();
        update |= mobileClientsPrivateNetworkAccessAcl.update();
        for (SquidAcl acl : vpnAcls.values()) {
            update |= acl.update();
        }

        if (update) {
            reloadingService.tellSquidToReloadConfig();
        }
    }

    private void writeKeys() {
        EblockerCa ca = sslService.getCa();
        if (ca != null) {
            try (FileOutputStream keyOutputStream = new FileOutputStream(sslKeyFilePath)) {
                try (FileOutputStream certOutputStream = new FileOutputStream(sslCertFilePath)) {
                    PKI.storePrivateKey(ca.getKey(), keyOutputStream);
                    PKI.storeCertificate(ca.getCertificate(), certOutputStream);
                }
            } catch (CryptoException | IOException e) {
                log.error("writing ssl key / certificate for squid failed!", e);
            }
        }
    }

    private void clearCertificateCache() {
        try {
            scriptRunner.runScript(squidClearCertCacheScript);
        } catch (Exception e) {
            log.error("Could not clear squid certificate cache", e);
        }
    }
}
