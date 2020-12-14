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
package org.eblocker.server.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StaticFileControllerTest {
    private StaticFileController fileController;
    private Response response;
    private static Charset charset = StandardCharsets.UTF_8;
    private static int testDataCacheTime = 10;//in seconds
    private static String dashboardHost = "eblocker.box";
    private static String httpsPath = "https";
    private static String[] consolePaths = { "/console", "/console/", "/console/bla" };

    @Before
    public void setUp() throws Exception {
        URL testDataRoot = ClassLoader.getSystemResource("test-data/document-root");

        fileController = new StaticFileController(testDataRoot.toURI().getPath(),
            testDataCacheTime,
            dashboardHost, httpsPath);
        response = new Response();
    }

    private Request makeHttpGetRequest(String uri) {
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        Request request = new Request(httpRequest, null);
        return request;
    }

    @Test
    public void testCachingEnabled() throws Exception {
        String headerName = "Cache-Control";
        Request request = makeHttpGetRequest("/test.html");
        fileController.read(request, response);

        assertTrue(response.getHeader(headerName) != null);
        assertTrue(response.getHeader(headerName).equals("private,max-age=" + testDataCacheTime));
    }

    @Test
    public void readFile() throws IOException {
        Request request = makeHttpGetRequest("/test.html");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "text/html; charset=UTF-8");
        assertEquals(buffer.toString(charset), "<html><head></head><body>Static test file</body></html>");
    }

    @Test
    public void readFileCompressed() throws IOException {
        Request request = makeHttpGetRequest("/test.html");
        request.addHeader("Accept-Encoding", "gzip");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals("text/html; charset=UTF-8", response.getContentType());
        assertEquals("gzip", response.getHeader("Content-Encoding"));
        assertEquals(readResource("test-data/document-root/test.html.gz"), buffer);
    }

    @Test
    public void redirectToSettingsFromRoot() throws IOException {
        Request request = makeHttpGetRequest("/");
        fileController.read(request, response);
        assertEquals(HttpResponseStatus.MOVED_PERMANENTLY, response.getResponseStatus());
        assertEquals("/settings/", response.getHeader(HttpHeaderNames.LOCATION.toString()));
    }

    @Test
    public void redirectToSettingsFromSettings() throws IOException {
        Request request = makeHttpGetRequest("/settings/");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "text/html; charset=UTF-8");
        assertEquals(buffer.toString(charset), "<html><head></head><body>Static index file</body></html>");
    }

    @Test
    public void readSettingsIndex() throws IOException {
        Request request = makeHttpGetRequest("/settings/index.html");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "text/html; charset=UTF-8");
        assertEquals(buffer.toString(charset), "<html><head></head><body>Static index file</body></html>");
    }

    @Test(expected = NotFoundException.class)
    public void readInvalidIndex() throws IOException {
        Request request = makeHttpGetRequest("/bla/index.html");
        fileController.read(request, response);
    }

    @Test
    public void readIndexFileSubDirectory() throws IOException {
        Request request = makeHttpGetRequest("/directory/");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "text/html; charset=UTF-8");
        assertEquals(buffer.toString(charset), "<html><head></head><body>Static index file in sub-directory</body></html>");
    }

    @Test
    public void redirectSubDirectory() throws IOException {
        Request request = makeHttpGetRequest("/directory");
        fileController.read(request, response);
        assertEquals(HttpResponseStatus.MOVED_PERMANENTLY, response.getResponseStatus());
        assertEquals("/directory/", response.getHeader(HttpHeaderNames.LOCATION.toString()));
    }

    @Test
    public void readSvgImage() throws IOException {
        Request request = makeHttpGetRequest("/test.svg");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "image/svg+xml");
        String data = buffer.toString(charset);
        assertTrue(data.startsWith("<?xml"));
    }

    @Test(expected = NotFoundException.class)
    public void fileNotFound() throws IOException {
        Request request = makeHttpGetRequest("/the/file/that/wasnt.there");
        fileController.read(request, response);
    }

    @Test(expected = NotFoundException.class)
    public void notBelowDocumentRoot() throws IOException {
        Request request = makeHttpGetRequest("/../sample.xhtml");
        fileController.read(request, response);
    }

    @Test(expected = NotFoundException.class)
    public void doNotLeakInformation() throws IOException {
        Request request = makeHttpGetRequest("/../document-root/test.html");
        fileController.read(request, response);
    }

    @Test
    public void ignoreRedundantSlashes() throws IOException {
        Request request = makeHttpGetRequest("////directory//index.html");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "text/html; charset=UTF-8");
        assertEquals(buffer.toString(charset), "<html><head></head><body>Static index file in sub-directory</body></html>");
    }

    @Test
    public void readFileWithoutQueryString() throws IOException {
        Request request = makeHttpGetRequest("/test.svg?foo=bar");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "image/svg+xml");
        String data = buffer.toString(charset);
        assertTrue(data.startsWith("<?xml"));
    }

    @Test
    public void readFileWithoutFragment() throws IOException {
        Request request = makeHttpGetRequest("/test.html#fragment?nonQuery=Value");
        ByteBuf buffer = (ByteBuf) fileController.read(request, response);
        assertEquals(response.getContentType(), "text/html; charset=UTF-8");
        assertEquals(buffer.toString(charset), "<html><head></head><body>Static test file</body></html>");
    }

    @Test
    public void redirectToDashboardFromShortURLs() throws IOException {
        // Map: URL -> Path
        HashMap<String, String> urls = new HashMap<>();
        urls.put("http://eblocker.box/", "/dashboard/");
        urls.put("https://eblocker.box/", "/dashboard/");
        urls.put("http://eblocker.box/dashboard", "/dashboard/");
        urls.put("http://eblocker.box/console", "/dashboard/#!/console");

        for (Map.Entry<String, String> url : urls.entrySet()) {
            response = new Response();

            URL testUrl = new URL(url.getKey());
            Request request = makeHttpGetRequest(testUrl.getPath());
            request.addHeader("Host", testUrl.getHost());
            fileController.read(request, response);

            assertEquals(HttpResponseStatus.MOVED_PERMANENTLY, response.getResponseStatus());
            assertEquals(url.getValue(), response.getHeader(HttpHeaderNames.LOCATION.toString()));
        }
    }

    @Test
    public void doNotRedirectFromShortIfRightPathIsGiven() throws IOException {
        String[] hosts = { "eblocker.box" };
        String path = "/dashboard/";

        for (String host : hosts) {
            response = new Response();

            Request request = makeHttpGetRequest(path);
            request.addHeader("Host", host);
            fileController.read(request, response);

            assertEquals(HttpResponseStatus.OK, response.getResponseStatus());
        }
    }

    @Test
    public void redirectToConsole() throws IOException {
        String[] hosts = { "192.168.1.2", "controlbar.eblocker.com", "192.168.1.2:3000", "controlbar.eblocker.com:3000" };

        for (String host : hosts) {
            for (String path : consolePaths) {
                Request request = Mockito.mock(Request.class);

                Mockito.when(request.getHost()).thenReturn(host);
                Mockito.when(request.getPath()).thenReturn(path);
                response = new Response();

                fileController.read(request, response);

                assertEquals(HttpResponseStatus.MOVED_PERMANENTLY, response.getResponseStatus());
                assertEquals("/", response.getHeader(HttpHeaderNames.LOCATION.toString()));
            }
        }
    }

    private ByteBuf readResource(String resource) {
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(resource)) {
            ByteBuf out = Unpooled.buffer();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.writeBytes(buffer, 0, read);
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
