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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.InternetAccessContingent;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.migrations.DefaultEntities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ParentalControlServiceTest {
    private DataSource dataSource;
    private ParentalControlService.ParentalControlProfileChangeListener listener;
    private UserService userService;

    private Map<Integer, UserModule> sampleUsersById;
    private Map<String, UserProfileModule> sampleProfiles;

    private final int DEFAULT_PROFILE_ID = DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID;
    private final int ALICE_PROFILE_ID = 2;
    private final int BOB_PROFILE_ID = 3;
    private final int UNUSED_PROFILE_ID = 4;

    private final int ALICE_USER_ID = 1;
    private final int BOB_USER_ID = 2;

    private AtomicInteger id = new AtomicInteger(5);

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        userService = Mockito.mock(UserService.class);
        listener = Mockito.mock(ParentalControlService.ParentalControlProfileChangeListener.class);

        // Sample profiles
        sampleProfiles = new HashMap<>();
        sampleUsersById = new HashMap<>();

        // Default profile
        String defaultProfileName = "name-default";
        String defaultProfileDescription = "description-default";
        UserProfileModule defaultProfile = new UserProfileModule(
                DEFAULT_PROFILE_ID,
                "default profile",
                null,
                defaultProfileName,
                defaultProfileDescription,
                true,
                false,
                new HashSet<>(),
                new HashSet<>(),
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                new HashSet<>(),
                new HashMap<>(),
                null,
                false,
                null
        );
        defaultProfile.setBuiltin(true);
        sampleProfiles.put("default", defaultProfile);
        sampleUsersById.put(0, new UserModule(0, DEFAULT_PROFILE_ID, "default", null, null, null, true, null, Collections.emptyMap(), null, null, null));

        // Profile Alice
        String aliceProfileName = "PROFILE_FOR_USER_" + ALICE_USER_ID;
        String aliceProfileDescription = "description-alice";
        Set<Integer> aliceInaccessibleSites = new HashSet<>();
        aliceInaccessibleSites.add(1);
        aliceInaccessibleSites.add(2);
        aliceInaccessibleSites.add(3);
        aliceInaccessibleSites.add(4);
        aliceInaccessibleSites.add(5);
        UserProfileModule aliceProfile = new UserProfileModule(
                ALICE_PROFILE_ID,
                aliceProfileName,
                aliceProfileDescription,
                null,
                null,
                false,
                false,
                new HashSet<>(),
                aliceInaccessibleSites,
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                new HashSet<>(),
                new HashMap<>(),
                null,
                false,
                null
        );
        aliceProfile.setForSingleUser(true);
        sampleProfiles.put("alice", aliceProfile);
        sampleUsersById.put(ALICE_USER_ID, new UserModule(ALICE_USER_ID, ALICE_PROFILE_ID, "alice", null, null, null, false, null, Collections.emptyMap(), null, null, null));

        // Profile Bob
        String bobProfileName = "PROFILE_FOR_USER_" + BOB_USER_ID;
        String bobProfileDescription = "description-bob";
        Set<InternetAccessContingent> bobAccessContingent = new HashSet<>();
        bobAccessContingent.add(new InternetAccessContingent(1, 9, 20, 4));
        bobAccessContingent.add(new InternetAccessContingent(2, 14, 20, 2));
        bobAccessContingent.add(new InternetAccessContingent(3, 15, 20, 2));
        UserProfileModule bobProfile = new UserProfileModule(
                BOB_PROFILE_ID,
                bobProfileName,
                bobProfileDescription,
                null,
                null,
                false,
                false,
                new HashSet<>(),
                new HashSet<>(),
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                bobAccessContingent,
                new HashMap<>(),
                null,
                false,
                null
        );
        bobProfile.setForSingleUser(true);
        sampleProfiles.put("bob", bobProfile);
        sampleUsersById.put(BOB_USER_ID, new UserModule(BOB_USER_ID, BOB_PROFILE_ID, "bob", null, null, null, false, null, Collections.emptyMap(), null, null, null));

        // profile without user
        UserProfileModule unusedProfile = new UserProfileModule(
                UNUSED_PROFILE_ID,
                "unused-name",
                "unsued-description",
                null,
                null,
                false,
                false,
                Collections.emptySet(),
                Collections.emptySet(),
                UserProfileModule.InternetAccessRestrictionMode.NONE,
                Collections.emptySet(),
                Collections.emptyMap(),
                null,
                false,
                null
        );
        unusedProfile.setForSingleUser(true);
        sampleProfiles.put("unused", unusedProfile);

        // setup user service mock
        Mockito.when(userService.getUsers(Mockito.anyBoolean())).thenReturn(sampleUsersById.values());
        Mockito.when(userService.getUserById(Mockito.anyInt())).then(im -> sampleUsersById.get(im.getArgument(0)));

        when(dataSource.nextId(eq(UserProfileModule.class))).thenAnswer(invocation -> {
            return id.incrementAndGet();
        });

        when(dataSource.save(any(UserProfileModule.class), any(Integer.class))).thenAnswer(invocation -> {
            // return the saved UserProfile
            return invocation.getArgument(0);
        });

        when(dataSource.getAll(eq(UserModule.class))).thenReturn(new ArrayList<>(sampleUsersById.values()));
    }

    @After
    public void tearDown() {

    }

    private ParentalControlService createParentalControlService() {
        ParentalControlService parentalControlService = new ParentalControlService(dataSource, userService);
        parentalControlService.init();
        parentalControlService.addListener(listener);
        return parentalControlService;
    }

    @Test
    public void createNewProfile() {
        // Mock behaviour of dataSource
        List<UserProfileModule> getAllResult = new ArrayList<>();
        getAllResult.add(sampleProfiles.get("default"));
        getAllResult.add(sampleProfiles.get("alice"));
        getAllResult.add(sampleProfiles.get("bob"));
        when(dataSource.nextId(UserProfileModule.class)).thenReturn(4711);
        when(dataSource.getAll(eq(UserProfileModule.class))).thenReturn(getAllResult);
        when(dataSource.save(any(), eq(5))).thenReturn(sampleProfiles.get("bob"));
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));

        ParentalControlService parentalControlService = createParentalControlService();

        UserProfileModule bobProfile = sampleProfiles.get("bob");
        UserProfileModule newProfile = new UserProfileModule(
                null,
                "john", // unique name
                bobProfile.getDescription(),
                null,
                null,
                false,
                false,
                bobProfile.getAccessibleSitesPackages(),
                bobProfile.getInaccessibleSitesPackages(),
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                bobProfile.getInternetAccessContingents(),
                new HashMap<>(),
                null,
                false,
                null
        );

        newProfile = parentalControlService.storeNewProfile(newProfile);

        // Verify
        assertNotNull(newProfile);
        assertTrue(newProfile.getId() > 0);
        verify(dataSource).getAll(eq(UserProfileModule.class));
        verify(dataSource, Mockito.times(1)).nextId(eq(UserProfileModule.class));
        verify(dataSource, Mockito.times(1)).save(any(), eq(4711));
        verify(listener).onChange(newProfile);
    }

    @Test
    public void createNewProfileWithProvidedId() {
        // Mock behaviour of dataSource
        List<UserProfileModule> getAllResult = new ArrayList<>();
        getAllResult.add(sampleProfiles.get("default"));
        getAllResult.add(sampleProfiles.get("alice"));
        getAllResult.add(sampleProfiles.get("bob"));
        when(dataSource.getAll(eq(UserProfileModule.class))).thenReturn(getAllResult);
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));

        ParentalControlService parentalControlService = createParentalControlService();

        UserProfileModule newProfile = sampleProfiles.get("bob");
        newProfile.setName("bob 2"); // to avoid name conflict

        // Test behaviour
        try {
            parentalControlService.storeNewProfile(newProfile);
            fail("expected BadRequestException because of provided ID in new profile");

        } catch (BadRequestException e) {
            // ok, expected this
        }

        // Verify
        verify(dataSource).getAll(eq(UserProfileModule.class));
        verify(dataSource, never()).save(sampleProfiles.get("bob"), BOB_PROFILE_ID);
        verify(listener, never()).onChange(Mockito.any());
    }

    @Test
    public void createNewProfileExistingName() {
        // Mock behaviour of dataSource
        List<UserProfileModule> getAllResult = new ArrayList<>();
        getAllResult.add(sampleProfiles.get("default"));
        getAllResult.add(sampleProfiles.get("alice"));
        getAllResult.add(sampleProfiles.get("bob"));
        when(dataSource.getAll(eq(UserProfileModule.class))).thenReturn(getAllResult);
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));

        ParentalControlService parentalControlService = createParentalControlService();

        UserProfileModule bobProfile = sampleProfiles.get("bob");
        UserProfileModule newProfile = new UserProfileModule(
                null,
                bobProfile.getName(),
                bobProfile.getDescription(),
                null,
                null,
                false,
                false,
                bobProfile.getAccessibleSitesPackages(),
                bobProfile.getInaccessibleSitesPackages(),
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                bobProfile.getInternetAccessContingents(),
                new HashMap<>(),
                null,
                false,
                null
        );

        // Test behaviour
        try {
            parentalControlService.storeNewProfile(newProfile);
            fail("expected ConflictException because of provided ID in new profile");

        } catch (ConflictException e) {
            // ok, expected this
        }

        // Verify
        verify(dataSource).getAll(eq(UserProfileModule.class));
        verify(dataSource, never()).save(sampleProfiles.get("bob"), BOB_PROFILE_ID);
    }

    @Test
    public void getProfiles() {
        // Mock behaviour of dataSource
        List<UserProfileModule> getAllResult = new ArrayList<>();
        getAllResult.add(sampleProfiles.get("default"));
        getAllResult.add(sampleProfiles.get("alice"));
        getAllResult.add(sampleProfiles.get("bob"));
        when(dataSource.getAll(eq(UserProfileModule.class))).thenReturn(getAllResult);
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));
        when(dataSource.get(eq(UserProfileModule.class), eq(UNUSED_PROFILE_ID))).thenReturn(sampleProfiles.get("unused"));

        ParentalControlService parentalControlService = createParentalControlService();

        // Test behaviour: actual list 'profiles' maintained in ParentalControlService
        List<UserProfileModule> result = parentalControlService.getProfiles();

        // Verify
        assertNotNull(result);
        verify(dataSource).getAll(eq(UserProfileModule.class));

        // ** only unused-profile is not in result list (see getAllResult above)
        for (UserProfileModule userProfileModule : result) {
            assertTrue(sampleProfiles.values().contains(userProfileModule));
        }
        for (UserProfileModule userProfileModule : sampleProfiles.values()) {
            if (!userProfileModule.getId().equals(UNUSED_PROFILE_ID)) {
                assertTrue(result.contains(userProfileModule));
            } else {
                assertFalse(result.contains(userProfileModule));
            }
        }
    }

    @Test
    public void updateProfile() {
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));

        // New version of profile "bob"
        String newBobName = "new bob name de";
        String newBobDescription = "new bob description de";
        Set<Integer> newBobAccessibleSites = new HashSet<>();
        newBobAccessibleSites.add(100);
        Set<Integer> newBobInaccessibleSites = new HashSet<>();
        newBobInaccessibleSites.add(110);
        Set<InternetAccessContingent> newBobContingents = new HashSet<>();
        newBobContingents.add(new InternetAccessContingent(4, 5, 6, 1));
        newBobContingents.add(new InternetAccessContingent(5, 9, 18, 3));
        UserProfileModule newBob = new UserProfileModule(
                BOB_PROFILE_ID,
                newBobName,
                newBobDescription,
                null,
                null,
                false,
                false,
                newBobAccessibleSites,
                newBobInaccessibleSites,
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                newBobContingents,
                new HashMap<>(),
                null,
                false,
                null
        );

        when(dataSource.save(eq(newBob), eq(BOB_PROFILE_ID))).thenReturn(newBob);
        ParentalControlService parentalControlService = createParentalControlService();

        // Test behaviour
        UserProfileModule savedProfile = parentalControlService.updateProfile(newBob);

        // Verify
        verify(dataSource, Mockito.times(1)).get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID));
        verify(dataSource).save(eq(newBob), eq(BOB_PROFILE_ID));
        verify(listener).onChange(savedProfile);
    }

    @Test
    public void updateProfileRenameBuiltin() {
        when(dataSource.get(eq(UserProfileModule.class), eq(DEFAULT_PROFILE_ID))).thenReturn(sampleProfiles.get("default"));
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));

        UserProfileModule oldDefault = sampleProfiles.get("default");

        // New version of profile "default"
        UserProfileModule newDefault = new UserProfileModule(
                oldDefault.getId(),
                null,
                null,
                "new-name",
                "new-description",
                false,
                false,
                oldDefault.getAccessibleSitesPackages(),
                oldDefault.getInaccessibleSitesPackages(),
                UserProfileModule.InternetAccessRestrictionMode.BLACKLIST,
                oldDefault.getInternetAccessContingents(),
                new HashMap<>(),
                null,
                false,
                null
        );
        newDefault.setBuiltin(false);

        when(dataSource.save(eq(newDefault), eq(DEFAULT_PROFILE_ID))).thenReturn(newDefault);
        ParentalControlService parentalControlService = createParentalControlService();

        // Try to update immutable values of builtin module
        newDefault = parentalControlService.updateProfile(newDefault);

        // assert that values have not changed
        assertEquals(oldDefault.getName(), newDefault.getName());
        assertEquals(oldDefault.getDescription(), newDefault.getDescription());
        assertEquals(oldDefault.isBuiltin(), newDefault.isBuiltin());

        // Verify
        verify(dataSource, Mockito.times(1)).get(eq(UserProfileModule.class), eq(DEFAULT_PROFILE_ID));
        verify(dataSource).save(eq(newDefault), eq(DEFAULT_PROFILE_ID));
        verify(listener).onChange(newDefault);
        verify(dataSource, Mockito.times(1)).save(any(UserProfileModule.class), any(Integer.class));
    }

    @Test
    public void deleteProfile() {
        // Device
        Device device = new Device();
        // Mock behaviour of dataSource
        Set<Device> devices = new HashSet<>();
        devices.add(device);
        when(dataSource.getDevices()).thenReturn(devices);
        when(dataSource.get(eq(UserProfileModule.class), eq(UNUSED_PROFILE_ID))).thenReturn(sampleProfiles.get("unused"));
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));
        Mockito.doNothing().when(dataSource).delete(eq(UserProfileModule.class), eq(UNUSED_PROFILE_ID));
        ParentalControlService parentalControlService = createParentalControlService();

        // Test behaviour
        parentalControlService.deleteProfile(UNUSED_PROFILE_ID);

        // Verify
        verify(dataSource).get(eq(UserProfileModule.class), eq(UNUSED_PROFILE_ID));
        verify(dataSource).delete(eq(UserProfileModule.class), eq(UNUSED_PROFILE_ID));
    }

    @Test
    public void deleteProfileDeviceAssigned() {
        // Mock behaviour of dataSource
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));
        ParentalControlService parentalControlService = createParentalControlService();

        // Test behaviour
        try {
            sampleUsersById.get(1).setAssociatedProfileId(BOB_PROFILE_ID);
            parentalControlService.deleteProfile(BOB_PROFILE_ID);
            fail("Expected ConflictException for deleting a used profile");

        } catch (ConflictException e) {
            // expected this
        }

        // Verify
        verify(dataSource, Mockito.times(1)).get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID));
        verify(dataSource, never()).delete(eq(UserProfileModule.class), eq(BOB_PROFILE_ID));
    }

    @Test
    public void deleteDefaultProfile() {
        // Device
        Device device = new Device();
        // Mock behaviour of dataSource
        Set<Device> devices = new HashSet<>();
        devices.add(device);
        when(dataSource.getDevices()).thenReturn(devices);
        when(dataSource.get(eq(UserProfileModule.class), eq(DEFAULT_PROFILE_ID))).thenReturn(sampleProfiles.get("default"));
        when(dataSource.get(eq(UserProfileModule.class), eq(ALICE_PROFILE_ID))).thenReturn(sampleProfiles.get("alice"));
        when(dataSource.get(eq(UserProfileModule.class), eq(BOB_PROFILE_ID))).thenReturn(sampleProfiles.get("bob"));
        Mockito.doNothing().when(dataSource).delete(eq(UserProfileModule.class), eq(DEFAULT_PROFILE_ID));
        ParentalControlService parentalControlService = createParentalControlService();

        // Test behaviour
        try {
            parentalControlService.deleteProfile(DEFAULT_PROFILE_ID);
            fail("Expected BadRequestException for deleting a builtin profile");

        } catch (BadRequestException e) {
            // expected this
        }

        // Verify
        verify(dataSource).get(eq(UserProfileModule.class), eq(DEFAULT_PROFILE_ID));
        verify(dataSource, never()).delete(eq(UserProfileModule.class), eq(BOB_PROFILE_ID));
    }
}
