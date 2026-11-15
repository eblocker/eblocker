package org.eblocker.server.common.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WireGuardAuthorizationDataModelTest {

    @Test
    public void newDevicePermissionDefaultsToDenied() {
        Device device = new Device();
        assertFalse(device.isWireGuardEnabled());
    }

    @Test
    public void newUserPermissionDefaultsToDenied() {
        UserModule user = user(7);
        assertFalse(user.isWireGuardEnabled());
    }

    @Test
    public void userPermissionSurvivesJsonRoundTrip() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        UserModule user = user(7);
        user.setWireGuardEnabled(true);

        String json = objectMapper.writeValueAsString(user);
        UserModule restored =
                objectMapper.readValue(json, UserModule.class);

        assertTrue(restored.isWireGuardEnabled());
    }

    private UserModule user(int id) {
        return new UserModule(
                id,
                1,
                "User",
                null,
                null,
                UserRole.OTHER,
                false,
                null,
                null,
                null,
                null,
                null);
    }
}
