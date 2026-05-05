package org.eblocker.server.http.backup;

import com.google.inject.Inject;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.UserService;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class UsersBackupProvider extends BackupProvider {
    public static final String USERS_ENTRY = "eblocker-config/users.json";

    private final UserService userService;
    private final ParentalControlService parentalControlService;
    private final DashboardCardService dashboardCardService;

    @Inject
    public UsersBackupProvider(UserService userService, ParentalControlService parentalControlService, DashboardCardService dashboardCardService) {
        this.userService = userService;
        this.parentalControlService = parentalControlService;
        this.dashboardCardService = dashboardCardService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        UsersBackup backup = createBackup();
        writeNextEntry(outputStream, USERS_ENTRY, objectMapper.writeValueAsBytes(backup));
    }

    private UsersBackup createBackup() {
        UsersBackup backup = new UsersBackup();
        Collection<UserModule> users = userService.getUsers(true);
        backup.setUsers(List.copyOf(users));
        backup.setProfiles(parentalControlService.getProfiles());
        backup.setUiCards(dashboardCardService.getAll());
        return backup;
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {

    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {

    }
}
