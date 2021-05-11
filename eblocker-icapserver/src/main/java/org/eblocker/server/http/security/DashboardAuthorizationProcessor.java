package org.eblocker.server.http.security;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.restexpress.Request;
import org.restexpress.pipeline.Preprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DashboardAuthorizationProcessor implements Preprocessor {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardAuthorizationProcessor.class);

    @Inject
    public DashboardAuthorizationProcessor() {

    }

    @Override
    public void process(Request request) {
        //LOG.warn("CONTEXT: {}\t{}\t{}\t{}", request.getAttachment("appContext"), request.getResolvedRoute().getName(), request.getHttpMethod(), request.getPath());
        // Only in appContext "dashboard":
        // TODO: check if the user of the requesting device is allowed to see the dashboard of the selected device.
    }
}
