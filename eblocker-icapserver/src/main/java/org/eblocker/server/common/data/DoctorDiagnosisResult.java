package org.eblocker.server.common.data;

public class DoctorDiagnosisResult {
    private final Severity severity;
    private final Audience audience;
    private final String message;

    public DoctorDiagnosisResult(Severity severity, Audience audience, String message) {
        this.severity = severity;
        this.audience = audience;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Audience getAudience() {
        return audience;
    }

    public String getMessage() {
        return message;
    }

    public enum Audience {
        NOVICE, EXPERT, EVERYONE
    }

    public enum Severity {
        RECOMMENDATION_NOT_FOLLOWED, HINT, FAILED_PROBE, ANORMALY, GOOD
    }
}
