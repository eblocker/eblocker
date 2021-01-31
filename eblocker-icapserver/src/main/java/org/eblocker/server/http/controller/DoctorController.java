package org.eblocker.server.http.controller;

import org.eblocker.server.common.data.DoctorDiagnosisResult;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.List;

public interface DoctorController {
    List<DoctorDiagnosisResult> runDiagnosis(Request request, Response response);
}
