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
package org.eblocker.server.common.squid;

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.http.service.DeviceService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SquidCacheLogReaderTest {

    private Path logPath;
    private DeviceService deviceService;
    private ExecutorService executorService;

    @Before
    public void setUp() throws IOException {
        logPath = Files.createTempFile("cache", ".log");

        deviceService = Mockito.mock(DeviceService.class);
        Device device = new Device();
        device.setId("device:001122334455");
        Mockito.when(deviceService.getDeviceByIp(IpAddress.parse("10.10.10.99"))).thenReturn(device);
        Mockito.when(deviceService.getDeviceByIp(IpAddress.parse("2a04:4711::1:2:3:4"))).thenReturn(device);

        executorService = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(logPath);
    }

    @Test(timeout = 5000)
    public void testReading() throws IOException, InterruptedException, CryptoException {
        SquidCacheLogReader reader = createLogReader();
        reader.start();

        writeLog("2018/01/15 08:14:11 kid1| WARNING: Consider increasing the number of ssl_crt_validator processes in your config file.");
        writeLog(
                "2018/01/15 08:19:58 kid1| eblkr: 20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY log_addr: 10.10.10.99:57608 host: 169.50.46.74 sni: api.vicomi.com cert: -----BEGIN CERTIFICATE-----\\nMIIFUzCCBDugAwIBAgIIWmapyhMGDvcwDQYJKoZIhvcNAQELBQAwgcYxCzAJBgNV\\nBAYTAlVTMRAwDgYDVQQIEwdBcml6b25hMRMwEQYDVQQHEwpTY290dHNkYWxlMSUw\\nIwYDVQQKExxTdGFyZmllbGQgVGVjaG5vbG9naWVzLCBJbmMuMTMwMQYDVQQLEypo\\ndHRwOi8vY2VydHMuc3RhcmZpZWxkdGVjaC5jb20vcmVwb3NpdG9yeS8xNDAyBgNV\\nBAMTK1N0YXJmaWVsZCBTZWN1cmUgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IC0gRzIw\\nHhcNMTcwOTEyMTMyMTAwWhcNMTgxMTExMTA0ODM4WjA6MSEwHwYDVQQLExhEb21h\\naW4gQ29udHJvbCBWYWxpZGF0ZWQxFTATBgNVBAMMDCoudmljb21pLmNvbTCCASIw\\nDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO6Qf5632T3gW0c7tV3SEJm/5We2\\n2I0OPdCDcVvnsNWvJY0khxqFsBnUJgAY9IuBzHKEM1EPJMtQvk18CrMvoYN7ij7E\\ngAlYgJqgHuRJkMY/ssCNyGdaajQzJIDwF9gWNGFd8vHDGB79LZQw50HksaIF88G3\\n5Y+3m31ZS9JNMAmeEfHT7P1Ls6nh37rCqFNNhkqsjzAuPuxGBqNLrIZ7VDMJdLg9\\n0JGANzquT/Y4VbZnpXQz1R1QqFQWrIIF7rHjmvYdPTpbZdPAa2rakmg6pKEKEEJm\\nV05vkb5dV6UgwUNzKPNOMhoZS2p/8Wef43gYaQll097fBdEiSwXXLT1CzmMCAwEA\\nAaOCAc4wggHKMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\\nAQUFBwMCMA4GA1UdDwEB/wQEAwIFoDA8BgNVHR8ENTAzMDGgL6AthitodHRwOi8v\\nY3JsLnN0YXJmaWVsZHRlY2guY29tL3NmaWcyczEtNjUuY3JsMGMGA1UdIARcMFow\\nTgYLYIZIAYb9bgEHFwEwPzA9BggrBgEFBQcCARYxaHR0cDovL2NlcnRpZmljYXRl\\ncy5zdGFyZmllbGR0ZWNoLmNvbS9yZXBvc2l0b3J5LzAIBgZngQwBAgEwgYIGCCsG\\nAQUFBwEBBHYwdDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3Auc3RhcmZpZWxkdGVj\\naC5jb20vMEYGCCsGAQUFBzAChjpodHRwOi8vY2VydGlmaWNhdGVzLnN0YXJmaWVs\\nZHRlY2guY29tL3JlcG9zaXRvcnkvc2ZpZzIuY3J0MB8GA1UdIwQYMBaAFCVFgWhQ\\nJjg9Oy0svs1q2bY9s2ZjMCMGA1UdEQQcMBqCDCoudmljb21pLmNvbYIKdmljb21p\\nLmNvbTAdBgNVHQ4EFgQU6KudzjrqKL4Cm/d0lkjRobX4+o8wDQYJKoZIhvcNAQEL\\nBQADggEBALLW/Q2U7/ZPeEU4dgibErP+BxMiHKn4ioUYNFATIermHQypZmkRGmY8\\nYP6KpNWS6JfWAkLmOwXfNSm1eaGFVO27FXHIS2bcYq9bwbyDbLzaJnlIkXv4IPmM\\nisnPUr7G02AdknHdjLVsV7wwqysSC7FPe3sYU8joDfWHePAt5U4CSCbN7q03Ckpf\\nhz/FXmGuUWA+tJWXmtZAeJ9ZYdYNMxMUH5+wZ4tT1ysFInA1dcdRqXLDvf+V8Ero\\nRba9j0lBLhLXjQIqqpax3SxQvZFoU1n2Bfm13j7TehA49mtcn5dRH9B5rYCBVbsY\\nMErDLyMsIaxC5xt6lKv783bPBN1lslw=\\n-----END CERTIFICATE-----\\n");
        writeLog("2018/01/15 13:00:23 kid1| default: Error negotiating SSL connection on FD 23: error:14094416:SSL routines:SSL3_READ_BYTES:sslv3 alert certificate unknown (1/0)");
        writeLog(
                "2018/01/15 13:32:15 kid1| eblkr: error:00000001:lib(0):func(0):reason(1) log_addr: 10.10.10.119:34890 host: <null> sni: api.weather.com cert: -----BEGIN CERTIFICATE-----\\nMIIFjTCCBTOgAwIBAgIQWsIWSlAawxKjwb2fboX4oDAKBggqhkjOPQQDAjCBgDEL\\nMAkGA1UEBhMCVVMxHTAbBgNVBAoTFFN5bWFudGVjIENvcnBvcmF0aW9uMR8wHQYD\\nVQQLExZTeW1hbnRlYyBUcnVzdCBOZXR3b3JrMTEwLwYDVQQDEyhTeW1hbnRlYyBD\\nbGFzcyAzIEVDQyAyNTYgYml0IFNTTCBDQSAtIEcyMB4XDTE3MTAxODAwMDAwMFoX\\nDTE4MTAxODIzNTk1OVowgYcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdHZW9yZ2lh\\nMRAwDgYDVQQHDAdBdGxhbnRhMSgwJgYDVQQKDB9UV0MgUHJvZHVjdCBhbmQgVGVj\\naG5vbG9neSwgTExDMRAwDgYDVQQLDAdEaWdpdGFsMRgwFgYDVQQDDA93d3cud2Vh\\ndGhlci5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR8SweyioC/YGrEI41P\\nq7OyekPKWqD9MO2V1R2GXAOKV+DeQvoy8yS5hCA56kOdfulRQMXEFN62X4fbH97i\\n/CBxo4IDhDCCA4AwggEwBgNVHREEggEnMIIBI4IQd3VuZGVyZ3JvdW5kLmNvbYIY\\nd3d3LmJ1c2luZXNzLndlYXRoZXIuY29tghIqLnd1bmRlcmdyb3VuZC5jb22CCHd4\\ndWcuY29tghFzdGcuY3JhemltYWxzLmNvbYIKKi53eHVnLmNvbYINY3JhemltYWxz\\nLmNvbYILd2VhdGhlci5jb22CCiouaW13eC5jb22CB3dzaS5jb22CDSoud2VhdGhl\\nci5jb22CEiouZmFzdGRhdGEuaWJtLmNvbYIGdy14LmNvggkqLndzaS5jb22CGXdh\\ndHNvbmFkdmVydGlzaW5nLmlibS5jb22CCCoudy14LmNvgghpbXd4LmNvbYIRd3d3\\nLmNyYXppbWFscy5jb22CD3d3dy53ZWF0aGVyLmNvbTAJBgNVHRMEAjAAMA4GA1Ud\\nDwEB/wQEAwIHgDBhBgNVHSAEWjBYMFYGBmeBDAECAjBMMCMGCCsGAQUFBwIBFhdo\\ndHRwczovL2Quc3ltY2IuY29tL2NwczAlBggrBgEFBQcCAjAZDBdodHRwczovL2Qu\\nc3ltY2IuY29tL3JwYTArBgNVHR8EJDAiMCCgHqAchhpodHRwOi8vcmMuc3ltY2Iu\\nY29tL3JjLmNybDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwHwYDVR0j\\nBBgwFoAUJfCK4Ut62QGVCu3GU/GMeB/Z8/gwVwYIKwYBBQUHAQEESzBJMB8GCCsG\\nAQUFBzABhhNodHRwOi8vcmMuc3ltY2QuY29tMCYGCCsGAQUFBzAChhpodHRwOi8v\\ncmMuc3ltY2IuY29tL3JjLmNydDCCAQQGCisGAQQB1nkCBAIEgfUEgfIA8AB2AN3r\\nHSt6DU+mIIuBrYFocH4ujp0B1VyIjT0RxM227L7MAAABXzBWmFkAAAQDAEcwRQIg\\nVE8gp+w3NSQmRH7X9YyjljQ6RQqpNS52gYHZVUkPvO0CIQChsL4UYFwcBBdu/qKn\\nACbFwFuqbNOOsyyA1fDEeQrriwB2AKS5CZC0GFgUh7sTosxncAo8NZgE+RvfuON3\\nzQ7IDdwQAAABXzBWmIcAAAQDAEcwRQIgV4i9RIL/aZHpcokn69UlecruIohv6iaS\\n6sBTRqqvLvwCIQDk6mQOACkIt6FWpBpCwymPLlwpm2YBhCnFwtuBuuzSjzAKBggq\\nhkjOPQQDAgNIADBFAiEA2j3XlhPMowRtle4jerxTN8+EpYd+DVnr6wPnB1YRRcsC\\nIEeH6Kz/yNvUYFnmA5nLb1Eit8mhCaZpcomCg4wLuFvJ\\n-----END CERTIFICATE-----\\n");
        writeLog(
                "2018/01/15 15:14:36 kid1| eblkr: error:00000005:lib(0):func(0):DH lib log_addr: 10.10.10.99:41486 host: <null> sni: www.google.com cert: -----BEGIN CERTIFICATE-----\\nMIIDuzCCAqOgAwIBAgIIU4Px/idbvgswDQYJKoZIhvcNAQELBQAwSTELMAkGA1UE\\nBhMCVVMxEzARBgNVBAoTCkdvb2dsZSBJbmMxJTAjBgNVBAMTHEdvb2dsZSBJbnRl\\ncm5ldCBBdXRob3JpdHkgRzIwHhcNMTcxMjEzMTMwOTQxWhcNMTgwMzA3MTMwMTAw\\nWjBoMQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwN\\nTW91bnRhaW4gVmlldzETMBEGA1UECgwKR29vZ2xlIEluYzEXMBUGA1UEAwwOd3d3\\nLmdvb2dsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARKr5OUrAwZmnRD\\nOb9Fxi3sskR9wwCsBYuE05D9+Dq5hD7UKi6o2mBDJ0q1Qicn0rg7tOHezOOGo1MR\\nUlwA+qnvo4IBUTCCAU0wEwYDVR0lBAwwCgYIKwYBBQUHAwEwDgYDVR0PAQH/BAQD\\nAgeAMBkGA1UdEQQSMBCCDnd3dy5nb29nbGUuY29tMGgGCCsGAQUFBwEBBFwwWjAr\\nBggrBgEFBQcwAoYfaHR0cDovL3BraS5nb29nbGUuY29tL0dJQUcyLmNydDArBggr\\nBgEFBQcwAYYfaHR0cDovL2NsaWVudHMxLmdvb2dsZS5jb20vb2NzcDAdBgNVHQ4E\\nFgQUcgxgaHW9xdqNbo1vh9z5Ln9E+UIwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAW\\ngBRK3QYWG7z2aLV29YG2u2IaulqBLzAhBgNVHSAEGjAYMAwGCisGAQQB1nkCBQEw\\nCAYGZ4EMAQICMDAGA1UdHwQpMCcwJaAjoCGGH2h0dHA6Ly9wa2kuZ29vZ2xlLmNv\\nbS9HSUFHMi5jcmwwDQYJKoZIhvcNAQELBQADggEBAF09DZQ/eQeNcJ3E/R/kaA0T\\nisQPRQCbr2MTZXH/YTvyV47TwMGjgj+dcH1bCH7C2foN+YSyZTchCRdaqbNxl7ZE\\nen2uj0w7rw2jp8zj2VdbL6APG3oqAieOQUSBlZTEosxj9U1XuWYhauPQc6pDuiBb\\nB84vSb+nXSxu4yktR55SNu0ZQGVMgGdx4xyyKlpScBCUCjrEYoYzyUsGL9TtO6xa\\nQYspm+QXESmHIhieLTPO1V8FOU7Q57to1A8/ToJM0EnqClbafTKzwGS3Rr0XE0zd\\ne2diHxZrx5emZVuWtLeZZxJTYoJlICWJyO2UjCYBY5G0B0XwUinBghekWvzusoQ=\\n-----END CERTIFICATE-----\\n");
        writeLog("2022/08/23 14:36:56 kid1| eblkr: io:0:(0) No error. log_addr: [2a04:4711::1:2:3:4]:54978 host: [2a03:2880:f22d:c5:face:b00c:0:167] sni: www.whatsapp.com cert: -----BEGIN CERTIFICATE-----\\nMIIGTzCCBTegAwIBAgIQAhmuP9z2KLNuVz09WEFiLzANBgkqhkiG9w0BAQsFADBw\\nMQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3\\nd3cuZGlnaWNlcnQuY29tMS8wLQYDVQQDEyZEaWdpQ2VydCBTSEEyIEhpZ2ggQXNz\\ndXJhbmNlIFNlcnZlciBDQTAeFw0yMjA2MDEwMDAwMDBaFw0yMjA4MzAyMzU5NTla\\nMGkxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRMwEQYDVQQHEwpN\\nZW5sbyBQYXJrMRcwFQYDVQQKEw5GYWNlYm9vaywgSW5jLjEXMBUGA1UEAwwOKi53\\naGF0c2FwcC5uZXQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQso374reIcxpf6\\n6fWOAJ1RDzBKf/rhXEErKxvPg0c9a0yQsvajV/OoCWUIUV27jyP80TjUog2yaiUA\\nRuPVOv0co4IDtTCCA7EwHwYDVR0jBBgwFoAUUWj/kK8CB3U8zNllZGKiErhZcjsw\\nHQYDVR0OBBYEFLRI8B8vopvbKoAwfyPcIWScMAxaMHQGA1UdEQRtMGuCDioud2hh\\ndHNhcHAubmV0ghIqLmNkbi53aGF0c2FwcC5uZXSCEiouc25yLndoYXRzYXBwLm5l\\ndIIOKi53aGF0c2FwcC5jb22CBXdhLm1lggx3aGF0c2FwcC5jb22CDHdoYXRzYXBw\\nLm5ldDAOBgNVHQ8BAf8EBAMCB4AwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsGAQUF\\nBwMCMHUGA1UdHwRuMGwwNKAyoDCGLmh0dHA6Ly9jcmwzLmRpZ2ljZXJ0LmNvbS9z\\naGEyLWhhLXNlcnZlci1nNi5jcmwwNKAyoDCGLmh0dHA6Ly9jcmw0LmRpZ2ljZXJ0\\nLmNvbS9zaGEyLWhhLXNlcnZlci1nNi5jcmwwPgYDVR0gBDcwNTAzBgZngQwBAgIw\\nKTAnBggrBgEFBQcCARYbaHR0cDovL3d3dy5kaWdpY2VydC5jb20vQ1BTMIGDBggr\\nBgEFBQcBAQR3MHUwJAYIKwYBBQUHMAGGGGh0dHA6Ly9vY3NwLmRpZ2ljZXJ0LmNv\\nbTBNBggrBgEFBQcwAoZBaHR0cDovL2NhY2VydHMuZGlnaWNlcnQuY29tL0RpZ2lD\\nZXJ0U0hBMkhpZ2hBc3N1cmFuY2VTZXJ2ZXJDQS5jcnQwCQYDVR0TBAIwADCCAYAG\\nCisGAQQB1nkCBAIEggFwBIIBbAFqAHcAKXm+8J45OSHwVnOfY6V35b5XfZxgCvj5\\nTV0mXCVdx4QAAAGBHO5pAQAABAMASDBGAiEAhivK6f3CfVXuVxEbltx25/KhnO1E\\nuk29AvD7/3Ti9fYCIQCReXm8hiwAZI74CHecLD2Rww0jE9h8OEZrXhCaCyL4HwB2\\nAEHIyrHfIkZKEMahOglCh15OMYsbA+vrS8do8JBilgb2AAABgRzuaS8AAAQDAEcw\\nRQIhAMObNd7hVl+SVtyl65La+dr8jRFBmbvuJa8iSL48/3gPAiBixjQXDiF9dT4z\\nqV4L/mWneaMarFg4KdE1sUgXaiL/KAB3AN+lXqtogk8fbK3uuF9OPlrqzaISpGpe\\njjsSwCBEXCpzAAABgRzuaTYAAAQDAEgwRgIhAMB4H509l2G+c5MwE9otDZSBM0sY\\nit+L5x3Azsyk6yoXAiEAtCsN1k7fV88FBqZdIwQTqXi9NKnmWoJLl9Atxz/FQ7gw\\nDQYJKoZIhvcNAQELBQADggEBAH0HD3bR0kUsTnXWsbE7GTngZ1UVIOCN5QTe186q\\n1816DVru9aRX3qKXmu8CJHkHvv77EMDS+EoA2dkGDV5UEpJeZGoicTvapBDeBdms\\nm9FPchvOo2cJ0WOyuO2Bxmw0wIvkz+4EwbzcPnyI/4bac8Hs0e3aJkudl1R05KTF\\nGNHdGJWvriyjiI+vzF81wFY+xBiV4VwdTTSiHnw+QXuNwcKEmuZ0c0TdVgiF1IzC\\n5UP/SGJuWBL7XXJNt0EGkW+/xKE27whdYIDi6sfKHrPf5uUqctE48Ql0TnCbC1Ik\\nK0P4M5TYIV7OvcHayM4CTNSBS9LOrKKa4qEE29ltwsNECk4=\\n-----END CERTIFICATE-----\\n");
        // give log-reader some time to catch up
        Thread.sleep(500);

        List<FailedConnectionLogEntry> failedConnections = reader.pollFailedConnections();
        Assert.assertEquals(3, failedConnections.size());

        Assert.assertEquals(ZonedDateTime.of(2018, 1, 15, 8, 19, 58, 0, ZoneId.systemDefault()).toInstant(), failedConnections.get(0).getInstant());
        Assert.assertEquals("20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY", failedConnections.get(0).getError());
        Assert.assertEquals("device:001122334455", failedConnections.get(0).getDeviceId());
        Assert.assertEquals("169.50.46.74", failedConnections.get(0).getHost());
        Assert.assertEquals("api.vicomi.com", failedConnections.get(0).getSni());
        X509Certificate certificate = PKI.loadCertificate(new ByteArrayInputStream(failedConnections.get(0).getCertificate().getBytes()));
        Assert.assertEquals("*.vicomi.com", PKI.getCN(certificate));

        Assert.assertEquals(ZonedDateTime.of(2018, 1, 15, 15, 14, 36, 0, ZoneId.systemDefault()).toInstant(), failedConnections.get(1).getInstant());
        Assert.assertEquals("error:00000005:lib(0):func(0):DH lib", failedConnections.get(1).getError());
        Assert.assertEquals("device:001122334455", failedConnections.get(1).getDeviceId());
        Assert.assertNull(failedConnections.get(1).getHost());
        Assert.assertEquals("www.google.com", failedConnections.get(1).getSni());
        certificate = PKI.loadCertificate(new ByteArrayInputStream(failedConnections.get(1).getCertificate().getBytes()));
        Assert.assertEquals("www.google.com", PKI.getCN(certificate));

        Assert.assertEquals("device:001122334455", failedConnections.get(2).getDeviceId());
        Assert.assertEquals("2a03:2880:f22d:c5:face:b00c:0:167", failedConnections.get(2).getHost());
    }

    @Test(timeout = 5000)
    public void testSkipExistingLog() throws IOException, InterruptedException, CryptoException {
        writeLog("2018/01/15 08:14:11 kid1| WARNING: Consider increasing the number of ssl_crt_validator processes in your config file.");
        writeLog(
                "2018/01/15 08:19:58 kid1| eblkr: 20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY log_addr: 10.10.10.99:57608 host: 169.50.46.74 sni: api.vicomi.com cert: -----BEGIN CERTIFICATE-----\\nMIIFUzCCBDugAwIBAgIIWmapyhMGDvcwDQYJKoZIhvcNAQELBQAwgcYxCzAJBgNV\\nBAYTAlVTMRAwDgYDVQQIEwdBcml6b25hMRMwEQYDVQQHEwpTY290dHNkYWxlMSUw\\nIwYDVQQKExxTdGFyZmllbGQgVGVjaG5vbG9naWVzLCBJbmMuMTMwMQYDVQQLEypo\\ndHRwOi8vY2VydHMuc3RhcmZpZWxkdGVjaC5jb20vcmVwb3NpdG9yeS8xNDAyBgNV\\nBAMTK1N0YXJmaWVsZCBTZWN1cmUgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IC0gRzIw\\nHhcNMTcwOTEyMTMyMTAwWhcNMTgxMTExMTA0ODM4WjA6MSEwHwYDVQQLExhEb21h\\naW4gQ29udHJvbCBWYWxpZGF0ZWQxFTATBgNVBAMMDCoudmljb21pLmNvbTCCASIw\\nDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO6Qf5632T3gW0c7tV3SEJm/5We2\\n2I0OPdCDcVvnsNWvJY0khxqFsBnUJgAY9IuBzHKEM1EPJMtQvk18CrMvoYN7ij7E\\ngAlYgJqgHuRJkMY/ssCNyGdaajQzJIDwF9gWNGFd8vHDGB79LZQw50HksaIF88G3\\n5Y+3m31ZS9JNMAmeEfHT7P1Ls6nh37rCqFNNhkqsjzAuPuxGBqNLrIZ7VDMJdLg9\\n0JGANzquT/Y4VbZnpXQz1R1QqFQWrIIF7rHjmvYdPTpbZdPAa2rakmg6pKEKEEJm\\nV05vkb5dV6UgwUNzKPNOMhoZS2p/8Wef43gYaQll097fBdEiSwXXLT1CzmMCAwEA\\nAaOCAc4wggHKMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\\nAQUFBwMCMA4GA1UdDwEB/wQEAwIFoDA8BgNVHR8ENTAzMDGgL6AthitodHRwOi8v\\nY3JsLnN0YXJmaWVsZHRlY2guY29tL3NmaWcyczEtNjUuY3JsMGMGA1UdIARcMFow\\nTgYLYIZIAYb9bgEHFwEwPzA9BggrBgEFBQcCARYxaHR0cDovL2NlcnRpZmljYXRl\\ncy5zdGFyZmllbGR0ZWNoLmNvbS9yZXBvc2l0b3J5LzAIBgZngQwBAgEwgYIGCCsG\\nAQUFBwEBBHYwdDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3Auc3RhcmZpZWxkdGVj\\naC5jb20vMEYGCCsGAQUFBzAChjpodHRwOi8vY2VydGlmaWNhdGVzLnN0YXJmaWVs\\nZHRlY2guY29tL3JlcG9zaXRvcnkvc2ZpZzIuY3J0MB8GA1UdIwQYMBaAFCVFgWhQ\\nJjg9Oy0svs1q2bY9s2ZjMCMGA1UdEQQcMBqCDCoudmljb21pLmNvbYIKdmljb21p\\nLmNvbTAdBgNVHQ4EFgQU6KudzjrqKL4Cm/d0lkjRobX4+o8wDQYJKoZIhvcNAQEL\\nBQADggEBALLW/Q2U7/ZPeEU4dgibErP+BxMiHKn4ioUYNFATIermHQypZmkRGmY8\\nYP6KpNWS6JfWAkLmOwXfNSm1eaGFVO27FXHIS2bcYq9bwbyDbLzaJnlIkXv4IPmM\\nisnPUr7G02AdknHdjLVsV7wwqysSC7FPe3sYU8joDfWHePAt5U4CSCbN7q03Ckpf\\nhz/FXmGuUWA+tJWXmtZAeJ9ZYdYNMxMUH5+wZ4tT1ysFInA1dcdRqXLDvf+V8Ero\\nRba9j0lBLhLXjQIqqpax3SxQvZFoU1n2Bfm13j7TehA49mtcn5dRH9B5rYCBVbsY\\nMErDLyMsIaxC5xt6lKv783bPBN1lslw=\\n-----END CERTIFICATE-----\\n");
        writeLog("2018/01/15 13:00:23 kid1| default: Error negotiating SSL connection on FD 23: error:14094416:SSL routines:SSL3_READ_BYTES:sslv3 alert certificate unknown (1/0)");
        writeLog(
                "2018/01/15 13:32:15 kid1| eblkr: error:00000001:lib(0):func(0):reason(1) log_addr: 10.10.10.119:34890 host: <null> sni: api.weather.com cert: -----BEGIN CERTIFICATE-----\\nMIIFjTCCBTOgAwIBAgIQWsIWSlAawxKjwb2fboX4oDAKBggqhkjOPQQDAjCBgDEL\\nMAkGA1UEBhMCVVMxHTAbBgNVBAoTFFN5bWFudGVjIENvcnBvcmF0aW9uMR8wHQYD\\nVQQLExZTeW1hbnRlYyBUcnVzdCBOZXR3b3JrMTEwLwYDVQQDEyhTeW1hbnRlYyBD\\nbGFzcyAzIEVDQyAyNTYgYml0IFNTTCBDQSAtIEcyMB4XDTE3MTAxODAwMDAwMFoX\\nDTE4MTAxODIzNTk1OVowgYcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdHZW9yZ2lh\\nMRAwDgYDVQQHDAdBdGxhbnRhMSgwJgYDVQQKDB9UV0MgUHJvZHVjdCBhbmQgVGVj\\naG5vbG9neSwgTExDMRAwDgYDVQQLDAdEaWdpdGFsMRgwFgYDVQQDDA93d3cud2Vh\\ndGhlci5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR8SweyioC/YGrEI41P\\nq7OyekPKWqD9MO2V1R2GXAOKV+DeQvoy8yS5hCA56kOdfulRQMXEFN62X4fbH97i\\n/CBxo4IDhDCCA4AwggEwBgNVHREEggEnMIIBI4IQd3VuZGVyZ3JvdW5kLmNvbYIY\\nd3d3LmJ1c2luZXNzLndlYXRoZXIuY29tghIqLnd1bmRlcmdyb3VuZC5jb22CCHd4\\ndWcuY29tghFzdGcuY3JhemltYWxzLmNvbYIKKi53eHVnLmNvbYINY3JhemltYWxz\\nLmNvbYILd2VhdGhlci5jb22CCiouaW13eC5jb22CB3dzaS5jb22CDSoud2VhdGhl\\nci5jb22CEiouZmFzdGRhdGEuaWJtLmNvbYIGdy14LmNvggkqLndzaS5jb22CGXdh\\ndHNvbmFkdmVydGlzaW5nLmlibS5jb22CCCoudy14LmNvgghpbXd4LmNvbYIRd3d3\\nLmNyYXppbWFscy5jb22CD3d3dy53ZWF0aGVyLmNvbTAJBgNVHRMEAjAAMA4GA1Ud\\nDwEB/wQEAwIHgDBhBgNVHSAEWjBYMFYGBmeBDAECAjBMMCMGCCsGAQUFBwIBFhdo\\ndHRwczovL2Quc3ltY2IuY29tL2NwczAlBggrBgEFBQcCAjAZDBdodHRwczovL2Qu\\nc3ltY2IuY29tL3JwYTArBgNVHR8EJDAiMCCgHqAchhpodHRwOi8vcmMuc3ltY2Iu\\nY29tL3JjLmNybDAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwHwYDVR0j\\nBBgwFoAUJfCK4Ut62QGVCu3GU/GMeB/Z8/gwVwYIKwYBBQUHAQEESzBJMB8GCCsG\\nAQUFBzABhhNodHRwOi8vcmMuc3ltY2QuY29tMCYGCCsGAQUFBzAChhpodHRwOi8v\\ncmMuc3ltY2IuY29tL3JjLmNydDCCAQQGCisGAQQB1nkCBAIEgfUEgfIA8AB2AN3r\\nHSt6DU+mIIuBrYFocH4ujp0B1VyIjT0RxM227L7MAAABXzBWmFkAAAQDAEcwRQIg\\nVE8gp+w3NSQmRH7X9YyjljQ6RQqpNS52gYHZVUkPvO0CIQChsL4UYFwcBBdu/qKn\\nACbFwFuqbNOOsyyA1fDEeQrriwB2AKS5CZC0GFgUh7sTosxncAo8NZgE+RvfuON3\\nzQ7IDdwQAAABXzBWmIcAAAQDAEcwRQIgV4i9RIL/aZHpcokn69UlecruIohv6iaS\\n6sBTRqqvLvwCIQDk6mQOACkIt6FWpBpCwymPLlwpm2YBhCnFwtuBuuzSjzAKBggq\\nhkjOPQQDAgNIADBFAiEA2j3XlhPMowRtle4jerxTN8+EpYd+DVnr6wPnB1YRRcsC\\nIEeH6Kz/yNvUYFnmA5nLb1Eit8mhCaZpcomCg4wLuFvJ\\n-----END CERTIFICATE-----\\n");

        SquidCacheLogReader reader = createLogReader();
        reader.start();

        writeLog(
                "2018/01/15 15:14:36 kid1| eblkr: error:00000005:lib(0):func(0):DH lib log_addr: 10.10.10.99:41486 host: <null> sni: www.google.com cert: -----BEGIN CERTIFICATE-----\\nMIIDuzCCAqOgAwIBAgIIU4Px/idbvgswDQYJKoZIhvcNAQELBQAwSTELMAkGA1UE\\nBhMCVVMxEzARBgNVBAoTCkdvb2dsZSBJbmMxJTAjBgNVBAMTHEdvb2dsZSBJbnRl\\ncm5ldCBBdXRob3JpdHkgRzIwHhcNMTcxMjEzMTMwOTQxWhcNMTgwMzA3MTMwMTAw\\nWjBoMQswCQYDVQQGEwJVUzETMBEGA1UECAwKQ2FsaWZvcm5pYTEWMBQGA1UEBwwN\\nTW91bnRhaW4gVmlldzETMBEGA1UECgwKR29vZ2xlIEluYzEXMBUGA1UEAwwOd3d3\\nLmdvb2dsZS5jb20wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARKr5OUrAwZmnRD\\nOb9Fxi3sskR9wwCsBYuE05D9+Dq5hD7UKi6o2mBDJ0q1Qicn0rg7tOHezOOGo1MR\\nUlwA+qnvo4IBUTCCAU0wEwYDVR0lBAwwCgYIKwYBBQUHAwEwDgYDVR0PAQH/BAQD\\nAgeAMBkGA1UdEQQSMBCCDnd3dy5nb29nbGUuY29tMGgGCCsGAQUFBwEBBFwwWjAr\\nBggrBgEFBQcwAoYfaHR0cDovL3BraS5nb29nbGUuY29tL0dJQUcyLmNydDArBggr\\nBgEFBQcwAYYfaHR0cDovL2NsaWVudHMxLmdvb2dsZS5jb20vb2NzcDAdBgNVHQ4E\\nFgQUcgxgaHW9xdqNbo1vh9z5Ln9E+UIwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAW\\ngBRK3QYWG7z2aLV29YG2u2IaulqBLzAhBgNVHSAEGjAYMAwGCisGAQQB1nkCBQEw\\nCAYGZ4EMAQICMDAGA1UdHwQpMCcwJaAjoCGGH2h0dHA6Ly9wa2kuZ29vZ2xlLmNv\\nbS9HSUFHMi5jcmwwDQYJKoZIhvcNAQELBQADggEBAF09DZQ/eQeNcJ3E/R/kaA0T\\nisQPRQCbr2MTZXH/YTvyV47TwMGjgj+dcH1bCH7C2foN+YSyZTchCRdaqbNxl7ZE\\nen2uj0w7rw2jp8zj2VdbL6APG3oqAieOQUSBlZTEosxj9U1XuWYhauPQc6pDuiBb\\nB84vSb+nXSxu4yktR55SNu0ZQGVMgGdx4xyyKlpScBCUCjrEYoYzyUsGL9TtO6xa\\nQYspm+QXESmHIhieLTPO1V8FOU7Q57to1A8/ToJM0EnqClbafTKzwGS3Rr0XE0zd\\ne2diHxZrx5emZVuWtLeZZxJTYoJlICWJyO2UjCYBY5G0B0XwUinBghekWvzusoQ=\\n-----END CERTIFICATE-----\\n");

        // give log-reader some time to catch up
        Thread.sleep(500);

        List<FailedConnectionLogEntry> failedConnections = reader.pollFailedConnections();

        Assert.assertEquals(1, failedConnections.size());
        Assert.assertEquals(ZonedDateTime.of(2018, 1, 15, 15, 14, 36, 0, ZoneId.systemDefault()).toInstant(), failedConnections.get(0).getInstant());
        Assert.assertEquals("error:00000005:lib(0):func(0):DH lib", failedConnections.get(0).getError());
        Assert.assertEquals("device:001122334455", failedConnections.get(0).getDeviceId());
        Assert.assertNull(failedConnections.get(0).getHost());
        Assert.assertEquals("www.google.com", failedConnections.get(0).getSni());
        X509Certificate certificate = PKI.loadCertificate(new ByteArrayInputStream(failedConnections.get(0).getCertificate().getBytes()));
        Assert.assertEquals("www.google.com", PKI.getCN(certificate));
    }

    @Test(timeout = 5000)
    public void testStartStop() throws IOException, InterruptedException {
        SquidCacheLogReader reader = createLogReader();
        reader.start();

        writeLog(
                "2018/01/15 08:19:58 kid1| eblkr: 20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY log_addr: 10.10.10.99:57608 host: 169.50.46.74 sni: api.vicomi.com cert: -----BEGIN CERTIFICATE-----\\nMIIFUzCCBDugAwIBAgIIWmapyhMGDvcwDQYJKoZIhvcNAQELBQAwgcYxCzAJBgNV\\nBAYTAlVTMRAwDgYDVQQIEwdBcml6b25hMRMwEQYDVQQHEwpTY290dHNkYWxlMSUw\\nIwYDVQQKExxTdGFyZmllbGQgVGVjaG5vbG9naWVzLCBJbmMuMTMwMQYDVQQLEypo\\ndHRwOi8vY2VydHMuc3RhcmZpZWxkdGVjaC5jb20vcmVwb3NpdG9yeS8xNDAyBgNV\\nBAMTK1N0YXJmaWVsZCBTZWN1cmUgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IC0gRzIw\\nHhcNMTcwOTEyMTMyMTAwWhcNMTgxMTExMTA0ODM4WjA6MSEwHwYDVQQLExhEb21h\\naW4gQ29udHJvbCBWYWxpZGF0ZWQxFTATBgNVBAMMDCoudmljb21pLmNvbTCCASIw\\nDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO6Qf5632T3gW0c7tV3SEJm/5We2\\n2I0OPdCDcVvnsNWvJY0khxqFsBnUJgAY9IuBzHKEM1EPJMtQvk18CrMvoYN7ij7E\\ngAlYgJqgHuRJkMY/ssCNyGdaajQzJIDwF9gWNGFd8vHDGB79LZQw50HksaIF88G3\\n5Y+3m31ZS9JNMAmeEfHT7P1Ls6nh37rCqFNNhkqsjzAuPuxGBqNLrIZ7VDMJdLg9\\n0JGANzquT/Y4VbZnpXQz1R1QqFQWrIIF7rHjmvYdPTpbZdPAa2rakmg6pKEKEEJm\\nV05vkb5dV6UgwUNzKPNOMhoZS2p/8Wef43gYaQll097fBdEiSwXXLT1CzmMCAwEA\\nAaOCAc4wggHKMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\\nAQUFBwMCMA4GA1UdDwEB/wQEAwIFoDA8BgNVHR8ENTAzMDGgL6AthitodHRwOi8v\\nY3JsLnN0YXJmaWVsZHRlY2guY29tL3NmaWcyczEtNjUuY3JsMGMGA1UdIARcMFow\\nTgYLYIZIAYb9bgEHFwEwPzA9BggrBgEFBQcCARYxaHR0cDovL2NlcnRpZmljYXRl\\ncy5zdGFyZmllbGR0ZWNoLmNvbS9yZXBvc2l0b3J5LzAIBgZngQwBAgEwgYIGCCsG\\nAQUFBwEBBHYwdDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3Auc3RhcmZpZWxkdGVj\\naC5jb20vMEYGCCsGAQUFBzAChjpodHRwOi8vY2VydGlmaWNhdGVzLnN0YXJmaWVs\\nZHRlY2guY29tL3JlcG9zaXRvcnkvc2ZpZzIuY3J0MB8GA1UdIwQYMBaAFCVFgWhQ\\nJjg9Oy0svs1q2bY9s2ZjMCMGA1UdEQQcMBqCDCoudmljb21pLmNvbYIKdmljb21p\\nLmNvbTAdBgNVHQ4EFgQU6KudzjrqKL4Cm/d0lkjRobX4+o8wDQYJKoZIhvcNAQEL\\nBQADggEBALLW/Q2U7/ZPeEU4dgibErP+BxMiHKn4ioUYNFATIermHQypZmkRGmY8\\nYP6KpNWS6JfWAkLmOwXfNSm1eaGFVO27FXHIS2bcYq9bwbyDbLzaJnlIkXv4IPmM\\nisnPUr7G02AdknHdjLVsV7wwqysSC7FPe3sYU8joDfWHePAt5U4CSCbN7q03Ckpf\\nhz/FXmGuUWA+tJWXmtZAeJ9ZYdYNMxMUH5+wZ4tT1ysFInA1dcdRqXLDvf+V8Ero\\nRba9j0lBLhLXjQIqqpax3SxQvZFoU1n2Bfm13j7TehA49mtcn5dRH9B5rYCBVbsY\\nMErDLyMsIaxC5xt6lKv783bPBN1lslw=\\n-----END CERTIFICATE-----\\n");
        Thread.sleep(250);

        Assert.assertEquals(1, reader.pollFailedConnections().size());

        reader.stop();
        Thread.sleep(250);

        writeLog(
                "2018/01/15 08:19:58 kid1| eblkr: 20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY log_addr: 10.10.10.99:57608 host: 169.50.46.74 sni: api.vicomi.com cert: -----BEGIN CERTIFICATE-----\\nMIIFUzCCBDugAwIBAgIIWmapyhMGDvcwDQYJKoZIhvcNAQELBQAwgcYxCzAJBgNV\\nBAYTAlVTMRAwDgYDVQQIEwdBcml6b25hMRMwEQYDVQQHEwpTY290dHNkYWxlMSUw\\nIwYDVQQKExxTdGFyZmllbGQgVGVjaG5vbG9naWVzLCBJbmMuMTMwMQYDVQQLEypo\\ndHRwOi8vY2VydHMuc3RhcmZpZWxkdGVjaC5jb20vcmVwb3NpdG9yeS8xNDAyBgNV\\nBAMTK1N0YXJmaWVsZCBTZWN1cmUgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IC0gRzIw\\nHhcNMTcwOTEyMTMyMTAwWhcNMTgxMTExMTA0ODM4WjA6MSEwHwYDVQQLExhEb21h\\naW4gQ29udHJvbCBWYWxpZGF0ZWQxFTATBgNVBAMMDCoudmljb21pLmNvbTCCASIw\\nDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO6Qf5632T3gW0c7tV3SEJm/5We2\\n2I0OPdCDcVvnsNWvJY0khxqFsBnUJgAY9IuBzHKEM1EPJMtQvk18CrMvoYN7ij7E\\ngAlYgJqgHuRJkMY/ssCNyGdaajQzJIDwF9gWNGFd8vHDGB79LZQw50HksaIF88G3\\n5Y+3m31ZS9JNMAmeEfHT7P1Ls6nh37rCqFNNhkqsjzAuPuxGBqNLrIZ7VDMJdLg9\\n0JGANzquT/Y4VbZnpXQz1R1QqFQWrIIF7rHjmvYdPTpbZdPAa2rakmg6pKEKEEJm\\nV05vkb5dV6UgwUNzKPNOMhoZS2p/8Wef43gYaQll097fBdEiSwXXLT1CzmMCAwEA\\nAaOCAc4wggHKMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\\nAQUFBwMCMA4GA1UdDwEB/wQEAwIFoDA8BgNVHR8ENTAzMDGgL6AthitodHRwOi8v\\nY3JsLnN0YXJmaWVsZHRlY2guY29tL3NmaWcyczEtNjUuY3JsMGMGA1UdIARcMFow\\nTgYLYIZIAYb9bgEHFwEwPzA9BggrBgEFBQcCARYxaHR0cDovL2NlcnRpZmljYXRl\\ncy5zdGFyZmllbGR0ZWNoLmNvbS9yZXBvc2l0b3J5LzAIBgZngQwBAgEwgYIGCCsG\\nAQUFBwEBBHYwdDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3Auc3RhcmZpZWxkdGVj\\naC5jb20vMEYGCCsGAQUFBzAChjpodHRwOi8vY2VydGlmaWNhdGVzLnN0YXJmaWVs\\nZHRlY2guY29tL3JlcG9zaXRvcnkvc2ZpZzIuY3J0MB8GA1UdIwQYMBaAFCVFgWhQ\\nJjg9Oy0svs1q2bY9s2ZjMCMGA1UdEQQcMBqCDCoudmljb21pLmNvbYIKdmljb21p\\nLmNvbTAdBgNVHQ4EFgQU6KudzjrqKL4Cm/d0lkjRobX4+o8wDQYJKoZIhvcNAQEL\\nBQADggEBALLW/Q2U7/ZPeEU4dgibErP+BxMiHKn4ioUYNFATIermHQypZmkRGmY8\\nYP6KpNWS6JfWAkLmOwXfNSm1eaGFVO27FXHIS2bcYq9bwbyDbLzaJnlIkXv4IPmM\\nisnPUr7G02AdknHdjLVsV7wwqysSC7FPe3sYU8joDfWHePAt5U4CSCbN7q03Ckpf\\nhz/FXmGuUWA+tJWXmtZAeJ9ZYdYNMxMUH5+wZ4tT1ysFInA1dcdRqXLDvf+V8Ero\\nRba9j0lBLhLXjQIqqpax3SxQvZFoU1n2Bfm13j7TehA49mtcn5dRH9B5rYCBVbsY\\nMErDLyMsIaxC5xt6lKv783bPBN1lslw=\\n-----END CERTIFICATE-----\\n");
        Thread.sleep(250);

        Assert.assertTrue(reader.pollFailedConnections().isEmpty());

        reader.start();
        writeLog(
                "2018/01/15 08:19:58 kid1| eblkr: 20:X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY log_addr: 10.10.10.99:57608 host: 169.50.46.74 sni: api.vicomi.com cert: -----BEGIN CERTIFICATE-----\\nMIIFUzCCBDugAwIBAgIIWmapyhMGDvcwDQYJKoZIhvcNAQELBQAwgcYxCzAJBgNV\\nBAYTAlVTMRAwDgYDVQQIEwdBcml6b25hMRMwEQYDVQQHEwpTY290dHNkYWxlMSUw\\nIwYDVQQKExxTdGFyZmllbGQgVGVjaG5vbG9naWVzLCBJbmMuMTMwMQYDVQQLEypo\\ndHRwOi8vY2VydHMuc3RhcmZpZWxkdGVjaC5jb20vcmVwb3NpdG9yeS8xNDAyBgNV\\nBAMTK1N0YXJmaWVsZCBTZWN1cmUgQ2VydGlmaWNhdGUgQXV0aG9yaXR5IC0gRzIw\\nHhcNMTcwOTEyMTMyMTAwWhcNMTgxMTExMTA0ODM4WjA6MSEwHwYDVQQLExhEb21h\\naW4gQ29udHJvbCBWYWxpZGF0ZWQxFTATBgNVBAMMDCoudmljb21pLmNvbTCCASIw\\nDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAO6Qf5632T3gW0c7tV3SEJm/5We2\\n2I0OPdCDcVvnsNWvJY0khxqFsBnUJgAY9IuBzHKEM1EPJMtQvk18CrMvoYN7ij7E\\ngAlYgJqgHuRJkMY/ssCNyGdaajQzJIDwF9gWNGFd8vHDGB79LZQw50HksaIF88G3\\n5Y+3m31ZS9JNMAmeEfHT7P1Ls6nh37rCqFNNhkqsjzAuPuxGBqNLrIZ7VDMJdLg9\\n0JGANzquT/Y4VbZnpXQz1R1QqFQWrIIF7rHjmvYdPTpbZdPAa2rakmg6pKEKEEJm\\nV05vkb5dV6UgwUNzKPNOMhoZS2p/8Wef43gYaQll097fBdEiSwXXLT1CzmMCAwEA\\nAaOCAc4wggHKMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\\nAQUFBwMCMA4GA1UdDwEB/wQEAwIFoDA8BgNVHR8ENTAzMDGgL6AthitodHRwOi8v\\nY3JsLnN0YXJmaWVsZHRlY2guY29tL3NmaWcyczEtNjUuY3JsMGMGA1UdIARcMFow\\nTgYLYIZIAYb9bgEHFwEwPzA9BggrBgEFBQcCARYxaHR0cDovL2NlcnRpZmljYXRl\\ncy5zdGFyZmllbGR0ZWNoLmNvbS9yZXBvc2l0b3J5LzAIBgZngQwBAgEwgYIGCCsG\\nAQUFBwEBBHYwdDAqBggrBgEFBQcwAYYeaHR0cDovL29jc3Auc3RhcmZpZWxkdGVj\\naC5jb20vMEYGCCsGAQUFBzAChjpodHRwOi8vY2VydGlmaWNhdGVzLnN0YXJmaWVs\\nZHRlY2guY29tL3JlcG9zaXRvcnkvc2ZpZzIuY3J0MB8GA1UdIwQYMBaAFCVFgWhQ\\nJjg9Oy0svs1q2bY9s2ZjMCMGA1UdEQQcMBqCDCoudmljb21pLmNvbYIKdmljb21p\\nLmNvbTAdBgNVHQ4EFgQU6KudzjrqKL4Cm/d0lkjRobX4+o8wDQYJKoZIhvcNAQEL\\nBQADggEBALLW/Q2U7/ZPeEU4dgibErP+BxMiHKn4ioUYNFATIermHQypZmkRGmY8\\nYP6KpNWS6JfWAkLmOwXfNSm1eaGFVO27FXHIS2bcYq9bwbyDbLzaJnlIkXv4IPmM\\nisnPUr7G02AdknHdjLVsV7wwqysSC7FPe3sYU8joDfWHePAt5U4CSCbN7q03Ckpf\\nhz/FXmGuUWA+tJWXmtZAeJ9ZYdYNMxMUH5+wZ4tT1ysFInA1dcdRqXLDvf+V8Ero\\nRba9j0lBLhLXjQIqqpax3SxQvZFoU1n2Bfm13j7TehA49mtcn5dRH9B5rYCBVbsY\\nMErDLyMsIaxC5xt6lKv783bPBN1lslw=\\n-----END CERTIFICATE-----\\n");

        Thread.sleep(250);
        Assert.assertEquals(1, reader.pollFailedConnections().size());
    }

    private SquidCacheLogReader createLogReader() throws IOException {
        return new SquidCacheLogReader(logPath.toString(), 100L, deviceService, executorService);
    }

    private void writeLog(String line) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(logPath, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.write('\n');
        }
    }
}
