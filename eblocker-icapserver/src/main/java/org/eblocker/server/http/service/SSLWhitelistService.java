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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class SSLWhitelistService implements Observer {

    private static final Logger log = LoggerFactory.getLogger(SSLWhitelistService.class);

    private final AppModuleService appModuleService;
    private final SquidConfigController squidConfigController;

    private SimpleResource squidDomainWhitelistAclFile;//this file we write only the urls into, so that it can be used by squid to disable ssl bumping for these domains
    private SimpleResource squidIpWhitelistAclFile;// file to contain all whitelisted IPs

    @Inject
    public SSLWhitelistService(@Named("squid.ssl.domain.whitelist.acl.file.path") String sslWhitelistOnlyDomainsFilePath,
                               @Named("squid.ssl.ip.whitelist.acl.file.path") String whitelistIPsFilePath,
                               @Named("ssl.url.whitelist.user.add.file.path") String userAddList,
                               @Named("ssl.url.whitelist.user.remove.file.path") String userRemoveList,
                               SquidConfigController squidConfigController,
                               AppModuleService appModuleService
    ) {

        this.appModuleService = appModuleService;
        appModuleService.addObserver(this);

        this.squidConfigController = squidConfigController;

        //this file contains the current domain whitelist in squid ACL format (no names; just urls)
        this.squidDomainWhitelistAclFile = new SimpleResource(sslWhitelistOnlyDomainsFilePath);

        //this file contains the current IP whitelist in squid ACL format (no names; just IPs or IP ranges)
        this.squidIpWhitelistAclFile = new SimpleResource(whitelistIPsFilePath);

        handleLegacyUserWhitelistFiles(userAddList, userRemoveList);

        if (!ResourceHandler.exists(squidDomainWhitelistAclFile)) {
            //create temporary acl file (which will be copied to squid folde when squidconfigcontroller.tellsquidtoReconfigure()... is called)
            log.info("Temporary Squid acl file for whitelisted domains does not exist here {} ...creating it.", sslWhitelistOnlyDomainsFilePath);
            ResourceHandler.create(squidDomainWhitelistAclFile);
        }

        if (!ResourceHandler.exists(squidIpWhitelistAclFile)) {
            //create temporary acl file (which will be copied to squid folde when squidconfigcontroller.tellsquidtoReconfigure()... is called)
            log.info("Temporary Squid acl file for whitelisted IPs does not exist here {} ...creating it.", whitelistIPsFilePath);
            ResourceHandler.create(squidIpWhitelistAclFile);
        }

        //init squid acl file with loaded domain list
        writeOnlyURLsToFile(squidDomainWhitelistAclFile);
        // init squid acl file with list of whitelisted IPs
        writeOnlyIPsToFile(squidIpWhitelistAclFile);
        squidConfigController.tellSquidToReloadConfig();
    }

    /**
     * Should be removed in one of the next versions.
     *
     * @param userAddList
     * @param userRemoveList
     */
    private void handleLegacyUserWhitelistFiles(String userAddList, String userRemoveList) {
        //this file contains all additions the user made to the default list
        EblockerResource userAddListFile = new SimpleResource(userAddList);

        if (ResourceHandler.exists(userAddListFile)) {
            Set<String> lines = ResourceHandler.readLinesAsSet(userAddListFile);
            if (lines != null) {
                List<SSLWhitelistUrl> domainsToAdd = lines.stream().
                        map(SSLWhitelistUrl::fromString).
                        filter(entry -> entry != null).collect(Collectors.toList());
                appModuleService.addDomainsToModule(domainsToAdd, appModuleService.getUserAppModuleId());
            }
        }
        ResourceHandler.replaceContent(userAddListFile, Collections.emptyList());

        //this file contains all the removals the user made from the default list
        EblockerResource userRemoveListFile = new SimpleResource(userRemoveList);

        if (ResourceHandler.exists(userRemoveListFile)) {
            Set<String> lines = ResourceHandler.readLinesAsSet(userRemoveListFile);
            if (lines != null) {
                List<SSLWhitelistUrl> domainsToRemove = lines.stream().
                        map(SSLWhitelistUrl::fromString).
                        filter(entry -> entry != null).collect(Collectors.toList());
                appModuleService.removeDomainsFromModule(domainsToRemove, appModuleService.getStandardAppModuleId());
            }
        }
        ResourceHandler.replaceContent(userRemoveListFile, Collections.emptyList());
    }

    /**
     * Add a domain to the SSL domain whitelist
     *
     * @param url  the domain
     * @param name the name of the owner or company or whatever owning the domain (can also be null)
     */
    public void addDomain(String url, String name) throws EblockerException {
        if (url != null) {

            if (UrlUtils.isInvalidDomain(url)) {
                // "Entering just a Top Level Domain like e.g. '.com' or '.de' is forbidden. url: " + url
                throw new EblockerException("INVALID_DOMAIN");

            } else {
                appModuleService.addDomainToModule(url, name, appModuleService.getUserAppModuleId());

            }
        }
    }

    /**
     * Remove a domain from the list
     *
     * @param url
     * @return
     */
    public void removeDomain(String url) {
        appModuleService.removeDomainFromModule(url, appModuleService.getUserAppModuleId());
    }

    /**
     * Writes all URLs to a file in a format that Squid ACL matching understands; that means we have to add a '.' as a prefix for all
     * domains, to make sure that all subdomains (including the domain that was entered) match too.
     *
     * @param file file to write in (replace the content)
     */
    private void writeOnlyURLsToFile(SimpleResource file) {
        //FIXME This might be a naive,unperformant solution with Sets -> we should use a tree datastructure here to find the subdomains we can remove
        if (!ResourceHandler.exists(file)) {
            ResourceHandler.create(file);
        }

        Set<String> urlList = new HashSet<>();

        //add all urls from the enabled appwhitelistmodules to the urlsList
        List<String> enabledAppModulesUrls = appModuleService.getAllUrlsFromEnabledModules().stream().map(String::toLowerCase).collect(Collectors.toList());
        if (enabledAppModulesUrls != null) {
            log.debug(Integer.toString(enabledAppModulesUrls.size()) + " URLs are added to SSL Exemption list, because they are linked to enabled AppWhitelistModules");
            urlList.addAll(enabledAppModulesUrls);
        }

        Set<String> notNeededUrls = new HashSet<>();
        List<String> results = new LinkedList<>();

        //collect all the notNeededUrls = subdomains
        for (String url : urlList) {
            for (String otherUrl : urlList) {
                //if not the same url, and otherUrl is subdomain of
                if (isSubdomainOf(otherUrl, url)) {
                    notNeededUrls.add(otherUrl);
                }
            }
        }
        log.info("ssl whitelist domain urls not needed in squid acl anymore: " + notNeededUrls.toString());

        //remove subdomains from list
        urlList.removeAll(notNeededUrls);

        //remove all blacklisted domains (e.g. for recording/analysis)
        urlList.removeAll(appModuleService.getBlacklistedDomains());

        //prepare format for squid acl file
        for (String url : urlList) {
            url = "." + url;//add the '.' for squid acl matching (to also include all subdomains)
            results.add(url);
        }
        //sort the list before writing
        Collections.sort(results);
        //write list to file
        ResourceHandler.replaceContent(file, results);
    }

    private void writeOnlyIPsToFile(SimpleResource file) {
        if (!ResourceHandler.exists(file)) {
            ResourceHandler.create(file);
        }

        Set<String> ipList = new HashSet<>();

        // add all IPs from the enabled appwhitelistmodules to the ipList
        List<String> enabledAppModulesIPs = appModuleService.getAllIPsFromEnabledModules();
        if (enabledAppModulesIPs != null) {
            log.debug(Integer.toString(enabledAppModulesIPs.size()) + " IPs are added to Exemption list, because they are linked to enabled AppWhitelistModules");
            ipList.addAll(enabledAppModulesIPs);
        }

        List<String> results = new LinkedList<>();

        // prepare format for squid acl file
        for (String ip : ipList) {
            results.add(ip);
        }
        // sort the list before writing
        Collections.sort(results);
        // write list to file
        ResourceHandler.replaceContent(file, results);
    }

    private boolean isSubdomainOf(String otherUrl, String url) {
        return !url.equals(otherUrl) && otherUrl.endsWith("." + url) && (otherUrl.length() > url.length());
    }

    @Override
    public synchronized void update(Observable observable, Object object) {
        if (observable instanceof AppModuleService && object != null && object instanceof List<?>) {
            //
            // The following block is just for logging!
            //
            for (Object element : (List<?>) object) {
                if (element instanceof AppWhitelistModule) {
                    AppWhitelistModule module = (AppWhitelistModule) element;
                    log.info("State of appWhitelistModule changed: enabled=" + module.isEnabled());

                    if (module.isEnabled()) {
                        // add urls of module to list
                        log.info("URLs of AppWhitelistModule: " + module.getName() + " are now added to the SSL Exemption list.");
                    } else {
                        // module got disabled
                        // remove from map
                        log.info("URLs of AppWhitelistModule: " + module.getName() + " are now removed from the SSL Exemption list.");
                    }
                }
            }

            //
            // rewrite acl file
            //
            writeOnlyURLsToFile(squidDomainWhitelistAclFile);
            writeOnlyIPsToFile(squidIpWhitelistAclFile);
            squidConfigController.tellSquidToReloadConfig();
        }
    }
}
