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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.data.messagecenter.provider.AppModuleRemovalMessageProvider;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class AppModuleService extends Observable {

    private static final Logger LOG = LoggerFactory.getLogger(AppModuleService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private final int tempAppModuleId;
    private final int standardAppModuleId;
    private final int userAppModuleId;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final AppModuleRemovalMessageProvider appModuleRemovalMessageProvider;

    private final EblockerResource builtinAppModulesResource;
    private Date lastBuiltinAppModulesUpdate;

    @Inject
    public AppModuleService(
            DataSource dataSource,
            ObjectMapper objectMapper,
            AppModuleRemovalMessageProvider appModuleRemovalMessageProvider,
            @Named("appmodules.file.path") String appModulesFilePath,
            @Named("appmodules.id.temp") int tempAppModuleId,
            @Named("appmodules.id.standard") int standardAppModuleId,
            @Named("appmodules.id.user") int userAppModuleId) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.appModuleRemovalMessageProvider = appModuleRemovalMessageProvider;
        this.builtinAppModulesResource = new SimpleResource(appModulesFilePath);
        this.tempAppModuleId = tempAppModuleId;
        this.standardAppModuleId = standardAppModuleId;
        this.userAppModuleId = userAppModuleId;
    }

    @SubSystemInit
    public void init() {
        cleanAndMergeStaticUserModules();
        updateFromBuiltin();
        provideDefaultAppModules();
    }

    private void cleanAndMergeStaticUserModules() {
        List<AppWhitelistModule> toBeMerged = new ArrayList<>();
        List<AppWhitelistModule> list = getAll();
        AppWhitelistModule originalModule = null;
        for (AppWhitelistModule each : list) {
            if (each.getId().equals(userAppModuleId) &&
                each.getName().equals("INTERNAL_USE_ONLY_SINGLE_ENTRIES_USERDEFINED")) {
                originalModule = each;
            } else if (!each.getId().equals(userAppModuleId) &&
                each.getName().startsWith("INTERNAL_USE_ONLY_SINGLE_ENTRIES_USERDEFINED")) {
                // add to be merged
                toBeMerged.add(each);
                // remove from DB
                LOG.warn("Deleting module with name {} and id {} ", each.getName(), each.getId());
                deleteBuiltinModule(each);
            }
        }

        if (originalModule == null && toBeMerged.size() > 0) {
            STATUS.info("Found inconsistent state: no original static app module, but {} broken one(s). Cleaning up..", toBeMerged.size());
            originalModule = new AppWhitelistModule(
                userAppModuleId,
                "INTERNAL_USE_ONLY_SINGLE_ENTRIES_USERDEFINED",
                Collections.emptyMap(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyMap(),
                true,
                true,
                true,
                false,
                null,
                false,
                true
            );
        }

        if (originalModule != null && toBeMerged.size() > 0) {

            List<String> oriBlacklist = originalModule.getBlacklistedDomains();
            List<String> oriWhitelistDomains = originalModule.getWhitelistedDomains();
            List<String> oriWhitelistIps = originalModule.getWhitelistedIPs();
            Map<String, String> oriLabels = originalModule.getLabels();

            for (AppWhitelistModule each : toBeMerged) {

                each.getBlacklistedDomains().stream()
                    .filter(d -> !oriBlacklist.contains(d))
                    .forEach(d -> oriBlacklist.add(d));

                each.getWhitelistedDomains().stream()
                    .filter(d -> !oriWhitelistDomains.contains(d))
                    .forEach(d -> oriWhitelistDomains.add(d));

                each.getWhitelistedIPs().stream()
                    .filter(d -> !oriWhitelistIps.contains(d))
                    .forEach(d -> oriWhitelistIps.add(d));

                // this may override an existing key, but while we merge we cannot know which key is "worth more",
                // the one in the original app module or the one in the wrongly created. Ideally each key is unique,
                // but it's unclear if the bug allowed to create duplicate keys in different app-duplicates
                oriLabels.putAll(each.getLabels());
            }

            originalModule.setBlacklistedDomains(oriBlacklist);
            originalModule.setWhitelistedDomains(oriWhitelistDomains);
            originalModule.setWhitelistedIPs(oriWhitelistIps);
            originalModule.setLabels(oriLabels);
            originalModule.setEnabled(true);

            dataSource.save(originalModule, originalModule.getId());

            storeEnabledState(originalModule);
            activateEnabledState(originalModule);
        }
        STATUS.info("Checked user defined static AppModules: {}", toBeMerged.size() > 0 ? "Inconsistencies found and removed" : "OK");
    }

    public Runnable getUpdater() {
        return this::updateFromBuiltin;
    }

    public int getTempAppModuleId() {
        return tempAppModuleId;
    }
    public int getStandardAppModuleId() {
        return standardAppModuleId;
    }
    public int getUserAppModuleId() {
        return userAppModuleId;
    }

    private void provideDefaultAppModules() {
        AppWhitelistModule module = get(tempAppModuleId);
        if (module == null) {
            module = new AppWhitelistModule(
                    tempAppModuleId,
                    "INTERNAL_USE_ONLY_TEST_RECORDING",
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    false,
                    false,
                    true,
                    false,
                    null,
                    false,
                    true
            );
            dataSource.save(module, tempAppModuleId);
        }
        module = get(standardAppModuleId);
        if (module == null) {
            module = new AppWhitelistModule(
                    standardAppModuleId,
                    "INTERNAL_USE_ONLY_SINGLE_ENTRIES_PREDEFINED",
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    true,
                    true,
                    true,
                    false,
                    null,
                    false,
                    true
            );
            dataSource.save(module, standardAppModuleId);
        }
        module = get(userAppModuleId);
        if (module == null) {
            module = new AppWhitelistModule(
                    userAppModuleId,
                    "INTERNAL_USE_ONLY_SINGLE_ENTRIES_USERDEFINED",
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    true,
                    true,
                    true,
                    false,
                    null,
                    false,
                    true
            );
            dataSource.save(module, userAppModuleId);
        }
    }

    /**
     * Used to identify hard coded modules. These modules should not be considered on actions like deletion of app
     * modules from the data store that have been removed from the list. These hard coded app modules are
     * obviously not contained in any list package by definition.
     * @param module
     * @return
     */
    private boolean isStaticModule(AppWhitelistModule module) {
        return module.getId().equals(userAppModuleId) ||
            module.getId().equals(tempAppModuleId);
    }

    /**
     * Load all app modules from database.
     *
     */
    public List<AppWhitelistModule> getAll() {
        List<AppWhitelistModule> modules = dataSource.getAll(AppWhitelistModule.class);
        for (AppWhitelistModule module: modules) {
            loadModuleEnabledState(module);
        }
        return modules;
    }

    public AppWhitelistModule get(int id) {
        AppWhitelistModule module = dataSource.get(AppWhitelistModule.class, id);
        if (module == null) {
            return null;
        }
        loadModuleEnabledState(module);
        return module;
    }

    /**
     * Save a new user-defined module. The module's ID must be null.
     * @param module to save. Note: the module is enabled automatically
     * @return
     */
    public AppWhitelistModule save(AppWhitelistModule module) {
        if (module.getId() != null) {
            throw new BadRequestException("Must not provide ID for new entity");
        }
        int id = dataSource.nextId(AppWhitelistModule.class);
        if (id <= 0) {
            throw new ConflictException("Cannot create new app module: No new id available");
        }

        //
        // Set some fields, which cannot be defined by the user
        //
        module.setId(id);
        module.setEnabledPerDefault(false);
        module.setEnabled(true);
        module.setBuiltin(false);
        module.setHidden(false);
        module.setModified(true);

        //
        // Validate and normalize the module name
        //
        normalizeAndValidateName(module);

        //
        // Validate and normalize the domain names
        //
        normalizeAndValidateDomains(module);

        //
        // Validate and normalize the IP addresses/ranges
        //
        normalizeAndValidateIPs(module);

        //
        // Save the module
        //
        AppWhitelistModule result = dataSource.save(module, module.getId());
        storeEnabledState(module);
        activateEnabledState(module);
        return result;
    }

    /**
     * Update an existing module.
     * @param module the module to update. Note: the enabled state is not updated.
     * @param id the module's ID
     * @return
     */
    public AppWhitelistModule update(AppWhitelistModule module, int id) {
        if (!Integer.valueOf(id).equals(module.getId())) {
            throw new BadRequestException("Cannot update app module: Mismatch of id");
        }
        //
        // Get the original version of the module (there must be one, as this is an update)!
        //
        AppWhitelistModule original = get(module.getId());
        if (original == null) {
            throw new ConflictException("Cannot find original version of entity with id "+id);
        }

        //
        // An update cannot change some of the fields - make sure that the module remains consistent
        //
        module.setEnabledPerDefault(original.isEnabledPerDefault());
        module.setEnabled(original.isEnabled());
        module.setBuiltin(original.isBuiltin());
        module.setHidden(original.isHidden());
        module.setModified(true);

        if (module.isBuiltin()) {
            module.setName(original.getName());
        }

        //
        // Validate and normalize the module name
        //
        if (module.getName() == null) {
            // Ok to leave out name in update
            module.setName(original.getName());
        }
        normalizeAndValidateName(module);

        //
        // Validate and normalize the domain names
        //
        normalizeAndValidateDomains(module);

        //
        // Validate and normalize the IP addresses/ranges
        //
        normalizeAndValidateIPs(module);

        //
        // Write changes to database
        //
        module = dataSource.save(module, module.getId());

        //
        // Has list of white listed domains been changed effectively?
        // Same for list of whitelisted IPs?
        // (Yes, if module is enabled and list of domains has changed.)
        //
        if (original.isEnabled() && (
                !original.getWhitelistedDomains().equals(module.getWhitelistedDomains())
                || !original.getWhitelistedIPs().equals(module.getWhitelistedIPs()
                ))) {
            activateEnabledState(module);
        }

        return module;
    }


    /**
     * Restore all modules that the user has modified or created, for example from a backup.
     * Also restore the enabled states given in the map.
     * @param modules the modules that the user has modified or created
     * @param enabledStates the enabled states of all modules
     */
    public void restoreModified(List<AppWhitelistModule> modules, Map<Integer, Boolean> enabledStates) {
        List<AppWhitelistModule> all = getAll();

        // Set the ID sequence
        int maxId = Stream.concat(all.stream(), modules.stream())
            .mapToInt(AppWhitelistModule::getId)
            .max()
            .getAsInt();
        dataSource.setIdSequence(AppWhitelistModule.class, maxId);

        // Restore modified modules
        modules.forEach(module -> {
            dataSource.save(module, module.getId());
        });

        // Restore enabled states of all modules
        List<AppWhitelistModule> modulesModifyState = getAll().stream()
            .filter(module -> {
                Boolean shouldEnable = enabledStates.get(module.getId());
                if (shouldEnable == null) {
                    return false; // do not modify state
                } else {
                    return module.isEnabled() != shouldEnable;
                }
            })
            .collect(Collectors.toList());

        modulesModifyState.forEach(module -> {
            module.setEnabled(enabledStates.get(module.getId()));
            storeEnabledState(module);
        });

        // Notify observers
        modulesModifyState.addAll(modules);
        activateEnabledState(modulesModifyState);
    }

    /**
     * Checks if module name is unique among customer created modules.
     * <p>
     * Please note that customer created modules may have the same name as builtin ones.
     *
     * @param id   id of module if it is a already persistent (may be null)
     * @param name name of module
     * @return if module name is unique among customer created modules.
     */
    public boolean isUniqueCustomerCreatedName(Integer id, String name) {
        Predicate<AppWhitelistModule> isCustomerCreatedModuleWithSameNameButDifferentId = m -> !m.isBuiltin() && m.getName().equals(name) && !m.getId().equals(id);
        return !dataSource.getAll(AppWhitelistModule.class).stream().filter(isCustomerCreatedModuleWithSameNameButDifferentId).findAny().isPresent();
    }

    public void delete(int id) {
        AppWhitelistModule module = get(id);
        if (module == null) {
            return;
        }
        if (module.isBuiltin()) {
            try {
                List<AppWhitelistModule> builtinModules = loadBuiltin(builtinAppModulesResource);
                for (AppWhitelistModule builtinModule : builtinModules) {
                    if (builtinModule.getId() == id) {
                        builtinModule.setEnabled(module.isEnabled());
                        dataSource.save(builtinModule, id);
                        activateEnabledState(module);
                        break;
                    }
                }
            } catch (BuiltinModulesLoadException e) {
                // just log error and ignore
                LOG.warn("failed to reset built-in module", e);
            }
        } else {
            dataSource.delete(AppWhitelistModule.class, id);
            if (module.isEnabled()) {
                module.setEnabled(false);
                storeEnabledState(module);
                activateEnabledState(module);
            }
        }
    }

    public void storeAndActivateEnabledState(int id, boolean enabled) {
        AppWhitelistModule updatedModule = get(id);
        if (updatedModule != null) {
            updatedModule.setEnabled(enabled);
            storeEnabledState(updatedModule);
            activateEnabledState(updatedModule);
        } else {
            LOG.warn("Could not find module with ID: "+id);
        }
    }

    private void storeEnabledState(AppWhitelistModule module) {
        int id = module.getId();
        boolean enabled = module.isEnabled();
        module.setEnabled(enabled);
        dataSource.setAppWhitelistModuleStatus(id, enabled);

        LOG.debug("Setting module '{}' [{}] to {}", module.getName(), id, enabled ? "<enabled>" : "<disabled>");
    }

    public void activateEnabledState(AppWhitelistModule module) {
        activateEnabledState(Collections.singletonList(module));
    }

    public void activateEnabledState(List<AppWhitelistModule> modules) {
        //tell SSLWhitelistDomainStore the state of this module changed
        setChanged();
        notifyObservers(modules);
    }

    private void loadModuleEnabledState(AppWhitelistModule module) {
        AppWhitelistModule.State state = dataSource.getAppModuleState(module.getId());
        switch (state) {
            case ENABLED:
                module.setEnabled(true);
                break;
            case DISABLED:
                module.setEnabled(false);
                break;
            case MODULE_NOT_FOUND:
            case DEFAULT:
            default:
                module.setEnabled(module.isEnabledPerDefault());
        }
    }

    /**
     * Get all URLs from all enabled AppWhitelistModules
     */
    public List<String> getAllUrlsFromEnabledModules() {
        return getAll().
                stream().
                filter(AppWhitelistModule::isEnabled).
                flatMap(m -> m.getWhitelistedDomains().stream()).
                collect(Collectors.toList());
    }

    public List<String> getBlacklistedDomains() {
        AppWhitelistModule tempModule = get(tempAppModuleId);
        if (tempModule == null || !tempModule.isEnabled()) {
            return Collections.emptyList();
        }
        return tempModule.getBlacklistedDomains();
    }

    /**
     * Get all IPs from all enabled AppWhitelistModules
     */
    public List<String> getAllIPsFromEnabledModules() {
        return getAll().
                stream().
                filter(AppWhitelistModule::isEnabled).
                flatMap(m -> m.getWhitelistedIPs().stream()).
                collect(Collectors.toList());
    }

    /**
     * Update app modules from provided built-in configuration.
     *
     * Attention: Any user-modified app module will not be updated!
     * The user would have to reset the module to get the latest built-in state.
     *
     */
    private void updateFromBuiltin() {
        // Was the file with modules changed since last update?
        Date builtinDate = ResourceHandler.getDate(builtinAppModulesResource);
        if (lastBuiltinAppModulesUpdate != null && lastBuiltinAppModulesUpdate.equals(builtinDate)) {
            return;
        }
        lastBuiltinAppModulesUpdate = builtinDate;

        // Load all modules
        List<AppWhitelistModule> builtinModules;
        try {
            builtinModules = loadBuiltin(builtinAppModulesResource);
        } catch (BuiltinModulesLoadException e) {
            LOG.error("Failed to load built-in modules", e);
            return;
        }

        updateFromBuiltinAddNewModules(builtinModules);

        Set<Integer> newModulesIds = builtinModules.stream().map(AppWhitelistModule::getId).collect(Collectors.toSet());
        // builtinModulesToBeDeleted is the list of all modules that are builtin and
        // (still) in the DB, but were not on disk (anymore)
        // --> disk meaning the file that was loaded by builtinAppModulesResource
        Map<Integer, AppWhitelistModule> builtinModulesToBeDeleted = getAll().stream()
                .filter(AppWhitelistModule::isBuiltin)
                .filter(m -> !isStaticModule(m)) // must not consider static modules (see hard coded modules above)
                .filter(m -> !newModulesIds.contains(m.getId()))
                .collect(Collectors.toMap(AppWhitelistModule::getId, Function.identity()));

        updateFromBuiltinRemoveModules(builtinModulesToBeDeleted);
    }

    private void updateFromBuiltinAddNewModules(List<AppWhitelistModule> builtinModules){
        Map<Integer, AppWhitelistModule> modulesById = getAll().stream()
            .filter(m -> !isStaticModule(m))  // must not consider static modules (see hard coded modules above)
            .collect(Collectors.toMap(AppWhitelistModule::getId, Function.identity()));


        List<AppWhitelistModule> newModules = new ArrayList<>();
        for (AppWhitelistModule builtin: builtinModules) {
            // This builtin module is still contained in the file, it has therefore not been deleted

            AppWhitelistModule module = modulesById.get(builtin.getId());
            if (module == null) {
                //
                // The builtin module is new (yet unknown): Save it to the DB
                //
                builtin.setEnabled(builtin.isEnabledPerDefault());
                dataSource.save(builtin, builtin.getId());
                //
                // Set and store default enabled state
                //
                storeEnabledState(builtin);
                //
                // If new module is enabled, remember it so that we can activate the new configuration later
                //
                if (builtin.isEnabled()) {
                    newModules.add(builtin);
                }
            } else if (!module.isModified()) {
                //
                // The builtin module has not been modified by the user:
                // Update it with the latest provided version.
                //
                // Attention: The 'enabled' state should not change!
                //
                builtin.setEnabled(modulesById.get(builtin.getId()).isEnabled());
                dataSource.save(builtin, builtin.getId());
            } else if (builtin.getVersion().equals(module.getVersion())) {
                //
                // The modified version is based on this built-in version, nothing to do in this case
            } else {
                //
                // Built-in version differs from base version of modified
                //
                // Informs user of new available version of modified app module. Idea is that we do not update the modified
                // app, so we do not override custom changes. But still inform the user that there is an update, so the user
                // can reset the app and get the version updated by us (but then loosing custom changes.)
                module.setUpdatedVersionAvailable(true);
                dataSource.save(module, module.getId());
            }
        }
        //
        // Activate updated modules
        //
        if (!newModules.isEmpty()) {
            activateEnabledState(newModules);
        }
    }

    /**
     * This method is used in the case that we remove a trusted app module from our list (here list is not
     * equivalent to table, but to our list packages):
     * After the user updates the list packages, the app module that has been removed from the list is still contained
     * in the data store. If the app module has not been modified, we can simply remove it from the data store.
     * If, however, the user has made changes, we do not want to remove these changes. So we create a custom app module
     * that is based on the app module that has been deleted from the list package. After which we can delete the
     * original app module from the data store, since the custom changes are within the newly created custom app module.
     * @param builtinModulesToBeDeleted all modules that are builtin and that have been removed from the list package,
     *                                  but are still contained in the data store
     */
    private void updateFromBuiltinRemoveModules(Map<Integer, AppWhitelistModule> builtinModulesToBeDeleted){
        Set<String> removedAppModulesNames = new HashSet<>();
        for (AppWhitelistModule builtinModuleToBeDeleted : builtinModulesToBeDeleted.values()) {
            if (builtinModuleToBeDeleted.isModified() || (!builtinModuleToBeDeleted.isEnabledPerDefault() && builtinModuleToBeDeleted.isEnabled())) {
                builtinModuleToBeDeleted.setBuiltin(false);
                // Preserve ID - must not be present for saving, must be present for deletion
                Integer tmpId = builtinModuleToBeDeleted.getId();
                // set ID to null, so that a new app module is created based on the old one
                builtinModuleToBeDeleted.setId(null);

                builtinModuleToBeDeleted.setName(findUniqueNameForAppModule(builtinModuleToBeDeleted.getName()));

                // create new module based on old one (prevent data loss, if user modified builtin app module)
                save(builtinModuleToBeDeleted);

                // Give ID back to original app module, so it can be removed later
                builtinModuleToBeDeleted.setId(tmpId);

                // Add name to display a message to the user
                removedAppModulesNames.add(builtinModuleToBeDeleted.getName());
            }
            // In any case, remove the builtin module
            deleteBuiltinModule(builtinModuleToBeDeleted);
        }
        appModuleRemovalMessageProvider.addRemovedAppModules(removedAppModulesNames);
    }

    private String findUniqueNameForAppModule(String appModuleNameBase) {
        int i = 0;
        String potentiallyUniqueName = appModuleNameBase;
        while (!isUniqueCustomerCreatedName(null, potentiallyUniqueName)) {
            i++;
            potentiallyUniqueName = appModuleNameBase + " #" + i;
        }
        return potentiallyUniqueName;
    }

    public void deleteBuiltinModule(AppWhitelistModule module) {
        if (module == null) {
            return;
        }
        dataSource.delete(AppWhitelistModule.class, module.getId());
        if (module.isEnabled()) {
            module.setEnabled(false);
            storeEnabledState(module);
            activateEnabledState(module);
        }
    }

    private List<AppWhitelistModule> loadBuiltin(EblockerResource builtinAppModulesResource) throws BuiltinModulesLoadException {
        try {
            LOG.debug("Loading appModules from file: "+builtinAppModulesResource.getPath());
            String jsonAppModules = ResourceHandler.load(builtinAppModulesResource);

            List<AppWhitelistModule> modules = objectMapper.readValue(jsonAppModules, new TypeReference<List<AppWhitelistModule>>(){});

            // if no explicit version is given generate one based on content
            for(AppWhitelistModule module : modules) {
                if (module.getVersion() == null) {
                    module.setVersion(calculateChecksum(module));
                }
            }

            return modules;
        } catch (EblockerException e) {
            throw new BuiltinModulesLoadException("Error while loading the builtin app modules JSON file", e);
        } catch (IOException e) {
            throw new BuiltinModulesLoadException("Error while parsing the builtin app modules JSON file", e);
        }
    }

    private void normalizeAndValidateName(AppWhitelistModule module) {
        //
        // The name must be unique
        //
        if (module.getName() == null) {
            throw new BadRequestException("Name is mandatory");
        }
        String name = module.getName().replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Normalized name is empty");
        }
        if (!module.isBuiltin() && !isUniqueCustomerCreatedName(module.getId(), module.getName())) {
            throw new ConflictException("Normalized name must be unique");
        }
        module.setName(name);
    }

    private void normalizeAndValidateDomains(AppWhitelistModule module) {
        if (module == null) {
            return;
        }
        List<String> normalizedDomains = new ArrayList<>();
        if (module.getWhitelistedDomains() != null) {
            for (String domain : module.getWhitelistedDomains()) {
                String normalizedDomain = domain.trim().replaceAll("\\s+", "");
                if (!normalizedDomain.isEmpty()) {
                    normalizedDomains.add(normalizedDomain);
                }
            }
        }
        module.setWhitelistedDomains(normalizedDomains);
    }

    private void normalizeAndValidateIPs(AppWhitelistModule module){
		if (module == null) {
			return;
		}
    	List<String> normalizedIPs = new ArrayList<>();
    	if (module.getWhitelistedIPs() != null){
    		for (String ip : module.getWhitelistedIPs()){
    			String normalizedIP = ip.trim().replaceAll("\\s+",  "");
    			if (!normalizedIP.isEmpty()){
    				normalizedIPs.add(normalizedIP);
    			}
    		}
    	}
    	module.setWhitelistedIPs(normalizedIPs);
    }

    public void addDomainToModule(String domain, String name, int id) {
        if (domain == null || domain.isEmpty()) {
            return;
        }
        AppWhitelistModule module = get(id);
        List<String> domains = new ArrayList<>(module.getWhitelistedDomains());
        Map<String, String> labels = new HashMap<>(module.getLabels());
        if (!domains.contains(domain)) {
            domains.add(domain);
        }
        if (name != null && !name.isEmpty()) {
            labels.put(domain, name);
        } else {
            if (labels.containsKey(domain)) {
                labels.remove(domain);
            }
        }
        module.setWhitelistedDomains(domains);
        module.setLabels(labels);
        update(module, id);
    }

    public void removeDomainFromModule(String domain, int id) {
        if (domain == null || domain.isEmpty()) {
            return;
        }
        AppWhitelistModule module = get(id);
        List<String> domains = new ArrayList<>(module.getWhitelistedDomains());
        Map<String, String> labels = new HashMap<>(module.getLabels());
        if (domains.contains(domain)) {
            domains.remove(domain);
        }
        if (labels.containsKey(domain)) {
            labels.remove(domain);
        }
        module.setWhitelistedDomains(domains);
        module.setLabels(labels);
        update(module, id);
    }

    public void addDomainsToModule(List<SSLWhitelistUrl> domainsToAdd, int id) {
        if (domainsToAdd == null || domainsToAdd.isEmpty()) {
            return;
        }
        AppWhitelistModule module = get(id);
        List<String> domains = new ArrayList<>(module.getWhitelistedDomains());
        Map<String, String> labels = new HashMap<>(module.getLabels());
        for (SSLWhitelistUrl domain: domainsToAdd) {
            if (!domains.contains(domain.getUrl())) {
                domains.add(domain.getUrl());
            }
            if (domain.getName() != null && !domain.getName().isEmpty()) {
                labels.put(domain.getUrl(), domain.getName());
            } else {
                if (labels.containsKey(domain.getUrl())) {
                    labels.remove(domain.getUrl());
                }
            }
        }
        module.setWhitelistedDomains(domains);
        module.setLabels(labels);
        update(module, id);
    }

    public void removeDomainsFromModule(List<SSLWhitelistUrl> domainsToRemove, int id) {
        if (domainsToRemove == null || domainsToRemove.isEmpty()) {
            return;
        }
        AppWhitelistModule module = get(id);
        List<String> domains = new ArrayList<>(module.getWhitelistedDomains());
        Map<String, String> labels = new HashMap<>(module.getLabels());
        for (SSLWhitelistUrl domain: domainsToRemove) {
            if (domains.contains(domain.getUrl())) {
                domains.remove(domain.getUrl());
            }
            if (labels.containsKey(domain.getUrl())) {
                labels.remove(domain.getUrl());
            }
        }
        module.setWhitelistedDomains(domains);
        module.setLabels(labels);
        update(module, id);
    }

    /**
     * Calculates checksum to be used as version for a module.
     *
     */
    private String calculateChecksum(AppWhitelistModule module) {
        // create a stable string representation order of members (maps iteration order isn't deterministic, so cannot use jackson here)
        StringBuilder builder = new StringBuilder();
        builder.append(module.getId());
        builder.append(module.getName());
        module.getBlacklistedDomains().forEach(builder::append);
        module.getWhitelistedDomains().forEach(builder::append);
        module.getWhitelistedIPs().forEach(builder::append);

        Consumer<Map<String, String>> appendKeyValues = m->m.entrySet().stream().map(e->e.getKey() + e.getValue()).sorted().forEach(builder::append);
        appendKeyValues.accept(module.getDescription());
        appendKeyValues.accept(module.getLabels());

        // calculate checksum (sha1)
        byte[] bytes = builder.toString().getBytes();
        Digest sha1 = new SHA1Digest();
        sha1.update(bytes, 0, bytes.length);
        byte[] digest = new byte[sha1.getDigestSize()];
        sha1.doFinal(digest, 0);
        return DatatypeConverter.printHexBinary(digest);
    }

    private class BuiltinModulesLoadException extends Exception {
        BuiltinModulesLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
