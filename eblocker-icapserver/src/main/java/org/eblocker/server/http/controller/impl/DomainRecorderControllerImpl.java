package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.common.recorder.DomainRequestRecorder;
import org.eblocker.server.common.recorder.RecordedDomainCounter;
import org.eblocker.server.http.controller.DomainRecorderController;
import org.eblocker.server.http.security.DashboardAuthorizationProcessor;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DomainRecorderControllerImpl implements DomainRecorderController {
    private static final Logger LOG = LoggerFactory.getLogger(DomainRecorderControllerImpl.class);
    private final DomainRequestRecorder domainRequestRecorder;

    @Inject
    public DomainRecorderControllerImpl(DomainRequestRecorder domainRequestRecorder) {
        this.domainRequestRecorder = domainRequestRecorder;
    }

    @Override
    public Map<String, RecordedDomainCounter> getRecordedDomains(Request request, Response response) {
        return domainRequestRecorder.getRecordedDomainRequests(getDeviceId(request));
    }

    @Override
    public void resetRecording(Request request, Response response) {
        domainRequestRecorder.resetRecording(getDeviceId(request));
    }

    private String getDeviceId(Request request) {
        String deviceId = request.getHeader(DashboardAuthorizationProcessor.DEVICE_ID_KEY);
        if (deviceId == null) {
            throw new NotFoundException("Could not find required parameter 'deviceId' in request");
        }
        return deviceId;
    }
}
