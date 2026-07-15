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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.TestRedisServer;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.JedisDataSource;
import org.eblocker.server.common.data.MacPrefix;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.common.data.dashboard.UiCardColumnPosition;
import org.eblocker.server.common.data.migrations.DefaultEntities;
import org.eblocker.server.common.network.IpResponseTable;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserAgentService;
import org.eblocker.server.http.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class UsersBackupProviderTest extends BackupProviderTestBase {
    /**
     * Create two DBSystems, one for exporting and one for importing the backup
     */
    @Test
    void testExportVerifyImport() throws IOException {
        DBSystem exp = new DBSystem();
        exp.start();
        UsersBackupProvider exportProvider = exp.createProvider();
        exp.setUpDefaultData();

        // Data to be backed up:
        Device device21 = exp.addDevice("device:abcdef000021", "192.168.0.21");
        Device device22 = exp.addDevice("device:abcdef000022", "192.168.0.22");
        UserModule parent = exp.addUser("Mom", UserRole.PARENT);
        UserModule child = exp.addUser("Billy the kid", UserRole.CHILD);
        exp.assignUser(child, device22);

        // edit dashboard: hide pause card in all layouts
        exp.setCardVisibility("PAUSE", false, parent);

        byte[] backup = exportBackup(exportProvider);
        exp.stop();

        DBSystem imp = new DBSystem();
        imp.start();
        UsersBackupProvider importProvider = imp.createProvider();
        imp.addUiCard("NEW_CARD"); // the IDs of UiCards in the target system can be different
        imp.setUpDefaultData();

        // Data to be overwritten by the backup import:
        Device device23 = imp.addDevice("device:abcdef000023", "192.168.0.23");
        UserModule otherUser = imp.addUser("To be removed", UserRole.OTHER);
        imp.assignUser(otherUser, device23);

        verifyBackup(backup, importProvider);
        importBackup(backup, importProvider);

        // Check imported data:
        Collection<Device> devices = imp.deviceService.getDevices(true);
        assertEquals(2, devices.size());
        Device out21 = imp.deviceService.getDeviceById(device21.getId());
        assertEquals(0, out21.getIpAddresses().size()); // IP addresses are removed!
        Device out22 = imp.deviceService.getDeviceById(device22.getId());
        UserModule childOut = imp.userService.getUserById(out22.getAssignedUser());
        assertNotNull(childOut);
        assertEquals(child.getName(), childOut.getName());
        parent = imp.getUserByName("Mom");
        imp.checkCardVisibility("PAUSE", false, parent);
        imp.checkUsersExist();

        // Overwritten data is gone:
        assertNull(imp.deviceService.getDeviceById(device23.getId()));
        assertNull(imp.userService.getUserById(otherUser.getId()));

        imp.stop();
    }

    /**
     * Creates a test Redis DB and services that work on it.
     */
    class DBSystem {
        TestRedisServer redis;
        ObjectMapper objectMapper;
        DataSource dataSource;
        DeviceFactory deviceFactory;
        DeviceService deviceService;
        DashboardCardService dashboardCardService;
        UserService userService;
        ParentalControlService parentalControlService;

        DBSystem() throws IOException {
            redis = new TestRedisServer();
            JedisPool pool = redis.getPool();
            objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            dataSource = new JedisDataSource(pool, objectMapper);
            DeviceRegistrationProperties deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
            UserAgentService userAgentService = Mockito.mock(UserAgentService.class);
            NetworkInterfaceWrapper networkInterfaceWrapper = Mockito.mock(NetworkInterfaceWrapper.class);
            MacPrefix macPrefix = new MacPrefix();
            deviceFactory = new DeviceFactory(dataSource, macPrefix);
            deviceService = new DeviceService(dataSource, deviceRegistrationProperties, userAgentService, networkInterfaceWrapper, deviceFactory, new IpResponseTable(), Clock.systemDefaultZone(), 120, macPrefix);
            dashboardCardService = new DashboardCardService(dataSource);
            userService = new UserService(dataSource, deviceService, dashboardCardService,"SHARED.USER.NAME.STANDARD_USER");
            parentalControlService = new ParentalControlService(dataSource, userService);
        }

        UsersBackupProvider createProvider() {
            return new UsersBackupProvider(deviceService, userService, parentalControlService, dashboardCardService, dataSource);
        }

        void start() {
            redis.start();
        }
        void stop() {
            redis.stop();
        }

        void setUpDefaultData() {
            parentalControlService.createDefaultProfile();
            dataSource.setIdSequence(UserProfileModule.class, DefaultEntities.PARENTAL_CONTROL_ID_SEQUENCE_USER_PROFILE_MODULE);
            addUiCard("PAUSE");
        }
        Device addDevice(String deviceId, String deviceIp) {
            Device device = deviceFactory.createDevice(deviceId, List.of(IpAddress.parse(deviceIp)), false);
            userService.restoreDefaultSystemUserAsUsers(device);
            deviceService.updateDevice(device);
            return device;
        }
        UserModule addUser(String userName, UserRole role) {
            UserProfileModule profile = new UserProfileModule(null, null,null, null, null, false, false, null, null, null, null, null, null, null, null);
            profile.setBuiltin(false);
            profile = parentalControlService.storeNewProfile(profile);
            return userService.createUser(profile.getId(), userName, "PARENTAL_CONTROL_USER_NAME", LocalDate.of(2001, 2, 3), role, "topsecret!");
        }
        void assignUser(UserModule user, Device device) {
            device.setAssignedUser(user.getId());
            device.setOperatingUser(user.getId());
            deviceService.updateDevice(device);
        }
        void addUiCard(String name) {
            int id = dataSource.nextId(UiCard.class);
            UiCard card = new UiCard(id, name, ProductFeature.BAS.name(), List.of(UserRole.PARENT, UserRole.OTHER), null);
            dashboardCardService.saveNewDashboardCard(card);
        }
        UiCard getCardByName(String cardName) {
            return dashboardCardService.getAll().stream().filter(c -> cardName.equals(c.getName())).findFirst().get();
        }
        void setCardVisibility(String cardName, boolean visible, UserModule user) {
            DashboardColumnsView view = user.getDashboardColumnsView();
            UiCard card = getCardByName(cardName);
            Function<UiCardColumnPosition, UiCardColumnPosition> mapper = (UiCardColumnPosition pos) ->
                    new UiCardColumnPosition(pos.getId(), pos.getColumn(), pos.getIndex(), pos.getId() == card.getId() ? visible : pos.isVisible(), pos.isExpanded());
            List<UiCardColumnPosition> oneColumn = view.getOneColumn().stream().map(mapper).collect(Collectors.toList());
            List<UiCardColumnPosition> twoColumn = view.getTwoColumn().stream().map(mapper).collect(Collectors.toList());
            List<UiCardColumnPosition> threeColumn = view.getThreeColumn().stream().map(mapper).collect(Collectors.toList());
            userService.updateUser(user.getId(), new DashboardColumnsView(oneColumn, twoColumn, threeColumn));
        }
        void checkCardVisibility(String cardName, boolean visible, UserModule user) {
            DashboardColumnsView view = user.getDashboardColumnsView();
            UiCard card = getCardByName(cardName);
            Consumer<UiCardColumnPosition> checker = (UiCardColumnPosition pos) -> {
                if (card.getId() == pos.getId()) {
                    assertEquals(visible, pos.isVisible());
                }
            };
            view.getOneColumn().stream().forEach(checker);
            view.getTwoColumn().stream().forEach(checker);
            view.getThreeColumn().stream().forEach(checker);
        }
        UserModule getUserByName(String name) {
            return userService.getUsers(true).stream().filter(user -> name.equals(user.getName())).findFirst().get();
        }
        void checkUsersExist() {
            for (Device device: deviceService.getDevices(true)) {
                assertNotNull(userService.getUserById(device.getDefaultSystemUser()));
                assertNotNull(userService.getUserById(device.getOperatingUser()));
                assertNotNull(userService.getUserById(device.getAssignedUser()));
            }
        }
    }
}