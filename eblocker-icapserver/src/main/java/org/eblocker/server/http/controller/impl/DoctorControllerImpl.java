package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.common.data.DoctorDiagnosisResult;
import org.eblocker.server.http.controller.DoctorController;
import org.eblocker.server.http.service.DoctorService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DoctorControllerImpl implements DoctorController {

    private static final Logger log = LoggerFactory.getLogger(DoctorControllerImpl.class);

    private final DoctorService doctorService;

    @Inject
    public DoctorControllerImpl(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @Override
    public List<DoctorDiagnosisResult> runDiagnosis(Request request, Response response) {
        return doctorService.runDiagnosis();
    }
}
