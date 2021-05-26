package org.eblocker.server.http.security;

import org.restexpress.Request;
import org.restexpress.pipeline.Preprocessor;

public interface DashboardAuthorizationProcessor extends Preprocessor {
    String VERIFY_DEVICE_ID = "VERIFY_DEVICE_ID";
    String VERIFY_USER_ID = "VERIFY_USER_ID";
    String DEVICE_ID_KEY = "deviceId";
    String USER_ID_KEY = "userId";

    @Override
    public void process(Request request);
}
