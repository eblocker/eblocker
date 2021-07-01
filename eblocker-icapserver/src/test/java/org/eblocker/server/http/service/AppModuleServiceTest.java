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

import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.data.messagecenter.provider.AppModuleRemovalMessageProvider;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AppModuleServiceTest extends EmbeddedRedisServiceTestBase {
    private AppModuleRemovalMessageProvider appModuleRemovalMessageProvider;

    @Before
    public void setUp() {
        appModuleRemovalMessageProvider = Mockito.mock(AppModuleRemovalMessageProvider.class);
    }

    @Test
    public void test_init() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        assertNotNull(appModuleService);

        List<AppWhitelistModule> builtinModules = appModuleService.getAll();
        assertNotNull(builtinModules);
        assertEquals(8, builtinModules.size());

        Map<Integer, AppWhitelistModule> map = builtinModules.stream().collect(Collectors.toMap(AppWhitelistModule::getId, Function.identity()));
        assertTrue(map.containsKey(1));
        assertTrue(map.containsKey(2));
        assertTrue(map.containsKey(3));
        assertTrue(map.containsKey(4));
        assertTrue(map.containsKey(appModuleService.getTempAppModuleId()));
        assertTrue(map.containsKey(appModuleService.getStandardAppModuleId()));
        assertTrue(map.containsKey(appModuleService.getUserAppModuleId()));
        assertTrue(map.containsKey(appModuleService.getAutoSslAppModule().getId()));
        assertEquals("OSX Softwareupdates", map.get(1).getName());
        assertEquals("Facebook Messenger App", map.get(2).getName());
        assertEquals("OSX App Store", map.get(3).getName());
        assertEquals("Windows 10 Updates", map.get(4).getName());

        assertEquals("FE5E06B0AF87EA23629BEBEE6AB6D72A5BCE1124", map.get(3).getVersion());
        assertEquals("explicitVersion", map.get(4).getVersion());

        assertEquals("Apple OSX App Store", map.get(3).getDescription().get("de"));
        assertEquals("Apple OSX App Store", map.get(3).getDescription().get("en"));
        assertEquals(4, map.get(3).getWhitelistedDomains().size());
        assertTrue(map.get(3).getWhitelistedDomains().contains("s.mzstatic.com"));
        assertTrue(map.get(3).getWhitelistedDomains().contains("swscan.apple.com"));
        assertTrue(map.get(3).getWhitelistedDomains().contains("xp.apple.com"));
        assertTrue(map.get(3).getWhitelistedDomains().contains("itunes.apple.com"));
        assertTrue(map.get(3).isEnabledPerDefault());
        assertTrue(map.get(3).isBuiltin());
        assertFalse(map.get(3).isModified());
        assertTrue(map.get(3).isEnabled());
    }

    @Test
    public void test_crud() throws IOException {
        dataSource.setIdSequence(AppWhitelistModule.class, 10000);

        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(2);
        assertNotNull(module);
        assertEquals("Facebook Messenger App", module.getName());
        assertEquals(5, module.getWhitelistedDomains().size());
        assertTrue(module.isBuiltin());
        assertFalse(module.isModified());
        assertTrue(module.getWhitelistedDomains().contains("b-api.facebook.com"));

        module.setWhitelistedDomains(Arrays.asList("domain1.com", "domain2.com", "domain3.com"));
        module.setBuiltin(false); // not allowed, should be ignored!
        appModuleService.update(module, 2);

        module = appModuleService.get(2);
        assertEquals(3, module.getWhitelistedDomains().size());
        assertFalse(module.getWhitelistedDomains().contains("b-api.facebook.com"));
        assertTrue(module.getWhitelistedDomains().contains("domain2.com"));
        assertTrue(module.isBuiltin()); // still builtin
        assertTrue(module.isModified()); // but now modified

        //
        // Try to "delete" a builtin module -> should not be deleted, but reset to defaults
        //
        appModuleService.delete(2);

        module = appModuleService.get(2);
        assertNotNull(module);
        assertEquals("Facebook Messenger App", module.getName());
        assertEquals(5, module.getWhitelistedDomains().size());
        assertTrue(module.isBuiltin());
        assertFalse(module.isModified());
        assertTrue(module.getWhitelistedDomains().contains("b-api.facebook.com"));

        //
        // Create a user defined module
        //
        module = appModuleService.save(createAppModule("My New Test Module", true));
        assertNotNull(module);
        assertNotNull(module.getId());
        int newId = module.getId();

        module = appModuleService.get(newId);
        assertNotNull(module);
        assertEquals("My New Test Module", module.getName());
        assertTrue(module.isEnabled());
        assertFalse(module.isBuiltin());
        assertTrue(module.isModified());

        //
        // Really delete user defined module
        //
        appModuleService.delete(newId);

        module = appModuleService.get(newId);
        assertNull(module);

    }

    @Test(expected = BadRequestException.class)
    public void test_save_withId() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        //
        // Create a user defined module
        //
        AppWhitelistModule module = createAppModule("My New Test Module", true);
        module.setId(4711); // invalid ID

        appModuleService.save(module);
    }

    @Test(expected = BadRequestException.class)
    public void test_save_withoutName() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        //
        // Create a user defined module
        //
        AppWhitelistModule module = createAppModule("My New Test Module", true);
        module.setName(null); // no name

        appModuleService.save(module);
    }

    @Test(expected = BadRequestException.class)
    public void test_save_emptyName() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        //
        // Create a user defined module
        //
        AppWhitelistModule module = createAppModule("My New Test Module", true);
        module.setName("   \t\n    \t"); // only whitespace

        appModuleService.save(module);
    }

    @Test(expected = ConflictException.class)
    public void test_save_nameNotUnique() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test-unique.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule m2 = appModuleService.get(2);
        //
        // Create a user defined module
        //
        AppWhitelistModule module = createAppModule("My New Test Module", true);
        module.setName(m2.getName()); // only whitespace

        appModuleService.save(module);
    }

    @Test // No error expected - empty domain list is allowed
    public void test_save_noWhitelistedDomains() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        //
        // Create a user defined module
        //
        AppWhitelistModule module = createAppModule("My New Test Module", true);
        module.setWhitelistedDomains(Collections.singletonList("   \t \t   ")); // only whitespace
        module.setWhitelistedIPs(Collections.singletonList("     \t    \t     \r    \n    ")); // only whitespace

        appModuleService.save(module);
    }

    @Test(expected = BadRequestException.class)
    public void test_update_wrongId() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(2);
        appModuleService.update(module, 3); // wrong id
    }

    @Test(expected = ConflictException.class)
    public void test_update_notExistingId() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(2);
        module.setId(4711);
        appModuleService.update(module, 4711);
    }

    @Test
    public void test_updateIp() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(3);
        module.setWhitelistedIPs(Arrays.asList("1.2.3.4", "2.3.4.5/24", "3.4.5.6/16"));
        appModuleService.update(module, 3);

        module = appModuleService.get(3);
        assertEquals(3, module.getWhitelistedIPs().size());
        assertFalse(module.getWhitelistedIPs().contains("99.99.99.99"));
        assertTrue(module.getWhitelistedIPs().contains("2.3.4.5/24"));
        assertTrue(module.isBuiltin()); // still builtin
        assertTrue(module.isModified()); // but now modified

        //
        // get all whitelisted IPs
        //
        //List<String> allWhitelistedIps = appModuleService.getAllIPsFromEnabledModules();
        assertEquals(3, module.getWhitelistedIPs().size());
        assertTrue(module.getWhitelistedIPs().contains("2.3.4.5/24"));

    }

    @Test
    public void test_update_noChange() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppModuleObserver observer = new AppModuleObserver(appModuleService);

        AppWhitelistModule module = appModuleService.get(2);
        appModuleService.update(module, 2);

        assertEquals(0, observer.size());
    }

    @Test
    public void test_delete_enabled() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        // Create custom module and enable it
        AppWhitelistModule module = appModuleService.save(createAppModule("My New Test Module", true));
        appModuleService.storeAndActivateEnabledState(module.getId(), true);

        AppModuleObserver observer = new AppModuleObserver(appModuleService);

        // delete enabled module
        appModuleService.delete(module.getId());

        // expect this to be observed
        assertEquals(1, observer.size());
    }

    @Test
    public void test_isUnique() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test-unique.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(0);
        assertTrue(appModuleService.isUniqueCustomerCreatedName(0, "Some other name"));
        assertTrue(appModuleService.isUniqueCustomerCreatedName(0, module.getName()));
        assertFalse(appModuleService.isUniqueCustomerCreatedName(1, module.getName()));
        assertFalse(appModuleService.isUniqueCustomerCreatedName(null, module.getName()));
    }

    @Test
    public void test_tempModule() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(appModuleService.getTempAppModuleId());
        assertNotNull(module);
        assertTrue(module.getName().startsWith("INTERNAL"));
        assertEquals(0, module.getWhitelistedDomains().size());
        assertTrue(module.isBuiltin());
        assertFalse(module.isModified());
        assertTrue(module.isHidden());

        module.setWhitelistedDomains(Arrays.asList("domain1.com", "domain2.com", "domain3.com"));
        module.setBuiltin(false); // not allowed, should be ignored!
        appModuleService.update(module, appModuleService.getTempAppModuleId());

        module = appModuleService.get(appModuleService.getTempAppModuleId());
        assertEquals(3, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("domain2.com"));
        assertTrue(module.isBuiltin()); // still builtin
        assertTrue(module.isModified()); // but now modified

        //
        // Try to "delete" a builtin module -> should not be deleted, but reset to defaults
        //
        appModuleService.delete(appModuleService.getTempAppModuleId());

        module = appModuleService.get(appModuleService.getTempAppModuleId());
        assertNotNull(module);

        module.setBlacklistedDomains(Arrays.asList("black1.com", "black2.com", "black3.com"));
        appModuleService.update(module, appModuleService.getTempAppModuleId());

        module = appModuleService.get(appModuleService.getTempAppModuleId());
        assertEquals(3, module.getBlacklistedDomains().size());
        assertTrue(module.getBlacklistedDomains().contains("black3.com"));

        //
        // Get all effective blacklisted domains --> expect 0, as temp module is not enabled
        //
        List<String> allBlacklistedDomains = appModuleService.getBlacklistedDomains();
        assertEquals(0, allBlacklistedDomains.size());

        // enable temp module
        appModuleService.storeAndActivateEnabledState(appModuleService.getTempAppModuleId(), true);

        // Check again list of all effective blacklisted domains
        allBlacklistedDomains = appModuleService.getBlacklistedDomains();
        assertEquals(3, allBlacklistedDomains.size());
        assertTrue(allBlacklistedDomains.contains("black2.com"));
    }

    @Test
    public void test_reloadBuiltin() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        //  The following cases will be tested:
        //    1. new module (id 17)
        //    2. updated modules (id 3)
        //    3. not updated because user modified the same version (id 4)
        //    4. not updated because user modified previous version but flagged as update available (id 2)

        // setup case 3:
        AppWhitelistModule case3Module = appModuleService.get(4);
        case3Module.setWhitelistedDomains(Arrays.asList("domain1.com", "domain2.com", "domain3.com"));
        appModuleService.update(case3Module, case3Module.getId());

        // setup case 4:
        AppWhitelistModule case4Module = appModuleService.get(2);
        case4Module.setWhitelistedDomains(Arrays.asList("domain4.com", "domain5.com", "domain6.com"));
        appModuleService.update(case4Module, case4Module.getId());

        // Load updated version of builtin modules.
        resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test-updated.json");
        appModuleService = createService(resourcePath);

        // case 1: new module (id 17)
        assertNotNull(appModuleService.get(17));
        assertTrue(appModuleService.get(17).getName().contains("new"));

        // case 2: updated module (id 3)
        assertTrue(appModuleService.get(3).getName().contains("updated"));

        // case 3: not updated because user modified the same version (id 4)
        assertFalse(appModuleService.get(4).isUpdatedVersionAvailable());
        assertTrue(appModuleService.get(4).getWhitelistedDomains().contains("domain1.com"));
        assertTrue(appModuleService.get(4).getWhitelistedDomains().contains("domain2.com"));
        assertTrue(appModuleService.get(4).getWhitelistedDomains().contains("domain3.com"));

        // case 4: not updated because user modified previous version but flagged as update available (id 2)
        assertTrue(appModuleService.get(2).isUpdatedVersionAvailable());
        assertTrue(appModuleService.get(2).getWhitelistedDomains().contains("domain4.com"));
        assertTrue(appModuleService.get(2).getWhitelistedDomains().contains("domain5.com"));
        assertTrue(appModuleService.get(2).getWhitelistedDomains().contains("domain6.com"));
        assertFalse(appModuleService.get(2).getWhitelistedDomains().contains("new-host.org"));
    }

    @Test
    public void test_modifiedBuiltinNotDeleted() throws IOException {
        String modName = "Modified Builtin Module";
        List<String> modDomains = Arrays.asList("first.domain.example", "second.domain.example");
        AppWhitelistModule case5Module = new AppWhitelistModule(5, modName, Collections.emptyMap(), modDomains,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, true, true, "1",
                false, false);
        dataSource.save(case5Module, case5Module.getId());

        Path resourcePath = ResourceTestUtil
                .provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        Optional<AppWhitelistModule> moduleAfterOpt = appModuleService.getAll().stream()
                .filter(m -> m.getName().equals(modName)).findAny();
        assertTrue(moduleAfterOpt.isPresent());
        AppWhitelistModule moduleAfter = moduleAfterOpt.get();
        assertFalse(moduleAfter.isBuiltin());
        assertTrue(moduleAfter.getWhitelistedDomains().equals(modDomains));
    }

    @Test
    public void test_modifiedBuiltinNotDeletedSecondModuleSameName() throws IOException {
        String modName = "Modified Builtin Module";
        List<String> modDomains = Arrays.asList("first.domain.example", "second.domain.example");
        AppWhitelistModule case5Module = new AppWhitelistModule(5, modName, Collections.emptyMap(), modDomains,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, true, true, "1",
                false, false);
        dataSource.save(case5Module, case5Module.getId());

        // The user has already created a second AppModule with the same name
        List<String> modDomainsUserDefined = Arrays.asList("first.user.defined", "second.user.defined");
        AppWhitelistModule case5ModuleUserDefined = new AppWhitelistModule(42, modName, Collections.emptyMap(),
                modDomainsUserDefined, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false,
                false, false, true, "1", false, false);
        dataSource.save(case5ModuleUserDefined, case5ModuleUserDefined.getId());

        Path resourcePath = ResourceTestUtil
                .provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        // Find the original module by its now-modified name with the number appended
        Optional<AppWhitelistModule> moduleAfterOpt = appModuleService.getAll().stream()
                .filter(m -> m.getName().equals(modName + " #1")).findAny();
        assertTrue(moduleAfterOpt.isPresent());
        AppWhitelistModule moduleAfter = moduleAfterOpt.get();
        assertFalse(moduleAfter.isBuiltin());
        assertTrue(moduleAfter.getWhitelistedDomains().equals(modDomains));
    }

    @Test
    public void test_notModifiedBuiltinDeleted() throws IOException {
        String modName = "Not Modified Builtin Module";
        List<String> modDomains = Arrays.asList("first.domain.example", "second.domain.example");
        AppWhitelistModule case5Module = new AppWhitelistModule(5, modName, Collections.emptyMap(), modDomains,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), false, false, true, false,
                "1", false, false);
        dataSource.save(case5Module, case5Module.getId());

        Path resourcePath = ResourceTestUtil
                .provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        Optional<AppWhitelistModule> moduleAfterOpt = appModuleService.getAll().stream()
                .filter(m -> m.getName().equals(modName)).findAny();
        assertFalse(moduleAfterOpt.isPresent());
    }

    @Test
    public void test_enableDisable() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppModuleObserver observer = new AppModuleObserver(appModuleService);

        appModuleService.storeAndActivateEnabledState(1, false);
        appModuleService.storeAndActivateEnabledState(2, false);
        appModuleService.storeAndActivateEnabledState(3, false);
        appModuleService.storeAndActivateEnabledState(4, false);

        assertEquals(4, observer.size());
        observer.clear();
        List<AppWhitelistModule> modules = appModuleService.getAll();
        for (AppWhitelistModule module : modules) {
            int id = module.getId();
            if (id == 1 || id == 2 || id == 3 || id == 4) {
                assertFalse(module.isEnabled());
            }
        }
        int defaultSize = appModuleService.getAllUrlsFromEnabledModules().size();

        appModuleService.storeAndActivateEnabledState(1, true);
        appModuleService.storeAndActivateEnabledState(2, true);
        appModuleService.storeAndActivateEnabledState(3, true);
        appModuleService.storeAndActivateEnabledState(4, true);

        assertEquals(4, observer.size());
        assertTrue(appModuleService.get(1).isEnabled());
        assertTrue(appModuleService.get(2).isEnabled());
        assertTrue(appModuleService.get(3).isEnabled());
        assertTrue(appModuleService.get(4).isEnabled());
        assertEquals(defaultSize + 15, appModuleService.getAllUrlsFromEnabledModules().size());

    }

    @Test
    public void test_deleteNotExisting() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        appModuleService.delete(4711);
        // should just be silently ignored
    }

    @Test
    public void test_enableNotExisting() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        appModuleService.storeAndActivateEnabledState(4711, true);
        // should just be silently ignored

        appModuleService.storeAndActivateEnabledState(4711, false);
        // should just be silently ignored
    }

    @Test
    public void test_invalidBuiltinResource() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/INVALID_FILE.json");
        AppModuleService appModuleService = createService(resourcePath);

        List<AppWhitelistModule> modules = appModuleService.getAll();
        assertEquals(4, modules.size()); // TEMP, STANDARD, USER, and auto-SSL modules are always there!
    }

    @Test
    public void test_missingBuiltinResource() throws IOException {
        Path resourcePath = FileSystems.getDefault().getPath("this file should not exist");

        // The service can still be created
        AppModuleService appModuleService = createService(resourcePath);

        List<AppWhitelistModule> modules = appModuleService.getAll();
        assertEquals(4, modules.size()); // TEMP, STANDARD, USER, and auto-SSL modules are always there!
    }

    @Test
    public void test_addRemoveDomain() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(3);
        int before = module.getWhitelistedDomains().size();

        // Add a domain with label
        appModuleService.addDomainToModule("my.domain.org", "My Domain", 3);

        module = appModuleService.get(3);
        assertEquals(before + 1, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain.org"));
        assertEquals("My Domain", module.getLabels().get("my.domain.org"));

        // Delete the label
        appModuleService.addDomainToModule("my.domain.org", null, 3);

        module = appModuleService.get(3);
        assertEquals(before + 1, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain.org"));
        assertFalse(module.getLabels().containsKey("my.domain.org"));

        // Add another label
        appModuleService.addDomainToModule("my.domain.org", "Other Label", 3);

        module = appModuleService.get(3);
        assertEquals(before + 1, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain.org"));
        assertEquals("Other Label", module.getLabels().get("my.domain.org"));

        // remove domain
        appModuleService.removeDomainFromModule("swscan.apple.com", 3);

        module = appModuleService.get(3);
        assertEquals(before, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain.org"));
        assertFalse(module.getWhitelistedDomains().contains("swscan.apple.com"));

        // remove other domain with label
        appModuleService.removeDomainFromModule("my.domain.org", 3);
        module = appModuleService.get(3);
        assertEquals(before - 1, module.getWhitelistedDomains().size());
        assertFalse(module.getWhitelistedDomains().contains("my.domain.org"));
        assertFalse(module.getLabels().containsKey("my.domain.org"));
    }

    @Test
    public void test_addRemoveDomains() throws IOException {
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        AppWhitelistModule module = appModuleService.get(3);
        int before = module.getWhitelistedDomains().size();

        // Add a domain with label
        appModuleService.addDomainsToModule(
                Arrays.asList(
                        new SSLWhitelistUrl("My Domain 1", "my.domain1.org"),
                        new SSLWhitelistUrl("My Domain 2", "my.domain2.org"),
                        new SSLWhitelistUrl("My Domain 3", "my.domain3.org"),
                        new SSLWhitelistUrl("My Domain 4", "my.domain4.org"),
                        new SSLWhitelistUrl(null, "swscan.apple.com") // already in rhe list!
                ), 3);

        module = appModuleService.get(3);
        assertEquals(before + 4, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain3.org"));
        assertEquals("My Domain 4", module.getLabels().get("my.domain4.org"));

        // Remove/change labels
        appModuleService.addDomainsToModule(
                Arrays.asList(
                        new SSLWhitelistUrl(null, "my.domain1.org"),
                        new SSLWhitelistUrl("", "my.domain2.org"),
                        new SSLWhitelistUrl("My Shiny Domain 3", "my.domain3.org"),
                        new SSLWhitelistUrl(null, "swscan.apple.com") // already in rhe list!
                ), 3);

        module = appModuleService.get(3);
        assertEquals(before + 4, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain3.org"));
        assertFalse(module.getLabels().containsKey("my.domain1.org"));
        assertFalse(module.getLabels().containsKey("my.domain2.org"));
        assertEquals("My Shiny Domain 3", module.getLabels().get("my.domain3.org"));

        // remove domain
        appModuleService.removeDomainsFromModule(
                Arrays.asList(
                        new SSLWhitelistUrl("My Domain 2", "my.domain2.org"),
                        new SSLWhitelistUrl("My Domain 4", "my.domain4.org"),
                        new SSLWhitelistUrl(null, "swscan.apple.com")
                ), 3);

        module = appModuleService.get(3);
        assertEquals(before + 1, module.getWhitelistedDomains().size());
        assertTrue(module.getWhitelistedDomains().contains("my.domain1.org"));
        assertFalse(module.getWhitelistedDomains().contains("my.domain2.org"));
        assertFalse(module.getWhitelistedDomains().contains("swscan.apple.com"));
    }

    @Test
    public void test_restore() throws IOException {
        final int idInternalTemp = 0;
        final int idOsxUpdates = 1;
        final int idFacebook = 2;
        final int idAppStore = 3;
        final int idWinUpdates = 4;
        final int idInternalUser = 9998;
        final int idInternalStandard = 9999;
        final int idUserDefined = 10042;

        // Create service
        Path resourcePath = ResourceTestUtil.provideResourceAsFile("test-data/appwhitelistmodules/app-whitelist-modules-test.json");
        AppModuleService appModuleService = createService(resourcePath);

        // Create data to be restored
        AppWhitelistModule userDefined = createAppModule("My New Test Module", true);
        userDefined.setId(idUserDefined);

        AppWhitelistModule builtIn = appModuleService.get(idFacebook);
        builtIn.setWhitelistedDomains(Arrays.asList("facebook.com"));

        AppWhitelistModule internal = appModuleService.get(idInternalUser);
        internal.setWhitelistedDomains(Arrays.asList("foo.de", "bar.com"));

        // Extract data to be restored
        List<AppWhitelistModule> modifiedModules = Arrays.asList(userDefined, builtIn, internal);
        Map<Integer, Boolean> enabledStates = new HashMap<Integer, Boolean>() {{
            put(idInternalTemp, false);
            put(idOsxUpdates, true);
            put(idFacebook, false);
            put(idAppStore, false); // disabled by user
            put(idWinUpdates, true); // enabled by user
            put(idInternalUser, true);
            put(idInternalStandard, true);
            put(idUserDefined, true); // My New Test Module
        }};

        // Check data before restoring
        assertNull(appModuleService.get(idUserDefined));
        assertTrue(appModuleService.get(idFacebook).getWhitelistedDomains().size() > 1);
        assertTrue(appModuleService.get(idInternalUser).getWhitelistedDomains().size() == 0);
        assertTrue(appModuleService.get(idAppStore).isEnabled());
        assertFalse(appModuleService.get(idWinUpdates).isEnabled());

        // Restore data
        AppModuleObserver observer = new AppModuleObserver(appModuleService);
        appModuleService.restoreModified(modifiedModules, enabledStates);

        // Check restored data
        assertEquals(5, observer.size()); // 3 modified modules and 2 enabled states
        assertEquals("My New Test Module", appModuleService.get(idUserDefined).getName());
        assertEquals(Arrays.asList("facebook.com"), appModuleService.get(idFacebook).getWhitelistedDomains());
        assertEquals(Arrays.asList("foo.de", "bar.com"), appModuleService.get(idInternalUser).getWhitelistedDomains());
        assertFalse(appModuleService.get(idAppStore).isEnabled());
        assertTrue(appModuleService.get(idWinUpdates).isEnabled());

        // Check that sequence was updated
        AppWhitelistModule nextModule = appModuleService.save(createAppModule("My Next Module", true));
        assertEquals(Integer.valueOf(idUserDefined + 1), nextModule.getId());
    }

    private AppModuleService createService(Path builtinAppModulesPath) {
        AppModuleService service = new AppModuleService(
                dataSource,
                objectMapper,
                appModuleRemovalMessageProvider,
                builtinAppModulesPath == null ? null : builtinAppModulesPath.toString(),
                0,
                9999,
                9998,
                9997
        );
        service.init();
        return service;
    }

    private AppWhitelistModule createAppModule(String name, boolean enabledbyDefault) {
        Map<String, String> description = new HashMap<>();
        description.put("de", "description de");
        description.put("en", "description en");
        return new AppWhitelistModule(
                null,
                name,
                description,
                IntStream.range(0, 10).mapToObj(i -> "domain-" + i + ".com").collect(Collectors.toList()),
                null,
                null,
                null,
                enabledbyDefault,
                false,
                null,
                null,
                null,
                null,
                null
        );
    }

    public class AppModuleObserver implements Observer {

        private final List<AppWhitelistModule> observed;

        public AppModuleObserver(AppModuleService appModuleService) {
            observed = new ArrayList<>();
            appModuleService.addObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            observed.addAll((List<AppWhitelistModule>) arg);

            observed.forEach((Object element) -> {
                assertTrue("All elements in the observed list should be AppWhitelistModules",
                        element instanceof AppWhitelistModule);
            });
        }

        public int size() {
            return observed.size();
        }

        public void clear() {
            observed.clear();
        }
    }
}
