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

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.restexpress.ContentType;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A RestExpress controller for serving static files (HTML, JS, CSS, ...)
 */
public class StaticFileController {
    private static final Logger log = LoggerFactory.getLogger(StaticFileController.class);
    private static final String HTTP_CACHE_HEADER = "Cache-Control";
    private static final String HTTP_EXPIRES_HEADER = "Expires";
    private static final String HTTP_DATE_HEADER = "Date";
    private static final String HTTP_LAST_MODIFIED_HEADER = "Last-Modified";
    private static final int HTTP_RESPONSE_CODE_NOT_MODIFIED = 304;
    private static final String HTTP_IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
    private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME;
    private static final String SETTINGS_URL = "/settings/";

    private int cacheElementsForSeconds;
    private Path documentRoot;
    private Map<Pattern, Path> aliases;
    private String dashboardHost;
    private String httpsWizardPath;

    @Inject
    public StaticFileController(
        @Named("documentRoot") String documentRoot,
        @Named("http.server.aliases.map") Map<String, String> aliases,
        @Named("http.server.cacheElementsForSeconds") int cacheTime,
        @Named("network.dashboard.host") String dashboardHost,
        @Named("network.https.wizard.path") String httpsWizardPath
    ) {
        this.documentRoot = FileSystems.getDefault().getPath(documentRoot);
        this.aliases = aliases.entrySet().stream().collect(Collectors.toMap(
            e -> Pattern.compile(e.getKey()),
            e -> FileSystems.getDefault().getPath(e.getValue())
        ));
        this.cacheElementsForSeconds = cacheTime;
        this.dashboardHost = dashboardHost;
        this.httpsWizardPath = httpsWizardPath;

        log.info("Created static file controller for directory: {}", documentRoot);
        log.info("Webserver-Elements are forced to be cached for {} seconds in Browser", cacheTime);
    }

    public StaticFileController(String documentRoot, int cacheTime, String dashboardUrls, String httpsWizardPath) {
        this(documentRoot, Collections.emptyMap(), cacheTime, dashboardUrls, httpsWizardPath);
    }

    /**
     * NOTE: this method is called from multiple threads simultaneously
     *
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    public Object read(Request request, Response response) throws IOException {
        String host = request.getHost();
        // skip slash at start
        String relativePath = request.getPath().substring(1);
        if (relativePath.contains("..")) {
            throw new NotFoundException("File not found: " + relativePath);
        }

        // strip fragment
        int fragmentIndex = relativePath.indexOf('#');
        if (fragmentIndex != -1) {
            relativePath = relativePath.substring(0, fragmentIndex);
        }

        // strip query string
        int queryIndex = relativePath.indexOf('?');
        if (queryIndex != -1) {
            relativePath = relativePath.substring(0, queryIndex);
        }

        //Redirect to console?
        if ("console".equals(relativePath) || relativePath.startsWith("console/")) {
            if (dashboardHost.equals(host)) {
                response.addLocationHeader("/dashboard/#!/console");
            } else {
                response.addLocationHeader("/");
            }
            response.setResponseStatus(HttpResponseStatus.MOVED_PERMANENTLY);
            return null;
        }

        // Redirect to dashboard by default
        if (dashboardHost.equals(host) && relativePath.length() == 0) {
            response.setResponseStatus(HttpResponseStatus.MOVED_PERMANENTLY);
            response.addLocationHeader("/dashboard/");
            return null;
        }

        if (httpsWizardPath.equals(relativePath)) {
            response.setResponseStatus(HttpResponseStatus.MOVED_PERMANENTLY);
            response.addLocationHeader("/dashboard/#!/httpsguide");
            return null;
        }

        if (relativePath.equals("index.html") || relativePath.length() == 0) {
            response.addLocationHeader(SETTINGS_URL);
            response.setResponseStatus(HttpResponseStatus.MOVED_PERMANENTLY);
            return null;
        }

        Path resolvedPath = getRootPath(relativePath);
        if (resolvedPath.toFile().isDirectory()) {
            if (relativePath.endsWith("/")) {
                resolvedPath = resolvedPath.resolve("index.html");
            } else {
                response.addLocationHeader("/" + relativePath + "/");
                response.setResponseStatus(HttpResponseStatus.MOVED_PERMANENTLY);
                return null;
            }
        }

        verifyFile(resolvedPath);

        Path path = resolvedPath;
        // check if client accepts compressed version and it is available
        String acceptEncodingValue = request.getHeader("Accept-Encoding");
        if (acceptEncodingValue != null) {
            for (String acceptedEncoding : acceptEncodingValue.split(",")) {
                if (acceptedEncoding.trim().equals(HttpHeaderValues.GZIP.toString())) {
                    Path compressedPath = resolvedPath.resolveSibling(resolvedPath.getFileName().toString() + ".gz");
                    if (compressedPath.toFile().exists()) {
                        path = compressedPath;
                        response.addHeader("Content-Encoding", HttpHeaderValues.GZIP.toString());
                    }
                    break;
                }
            }
        }

        // Add caching headers: these must be the same for 200 and 304 responses (see RFC 7232, section 4.1)
        addCachingOfFiles(response, path.toFile());

        //try to tell IE to use the edge renderer
        response.addHeader("X-UA-Compatible", "IE=edge");

        //CACHE VALIDATION -> which response code to return?!
        if (fileNotModified(path.toFile(), request)) {//return "304 - Not modified" to tell UA to take file from cache
            log.debug("File not modified {}", path);
            //set response code
            response.setResponseCode(HTTP_RESPONSE_CODE_NOT_MODIFIED);

            return null;//do not return the file
        } else {//return response code 200 - OK ; and send file
            String contentType = getContentType(resolvedPath);
            response.setContentType(contentType);

            byte[] bytes = Files.readAllBytes(path);

            log.debug(" --> Returning {} bytes", bytes.length);

            return Unpooled.wrappedBuffer(bytes);//send file in response body
        }
    }

    private Path getRootPath(String relativePath) {
        for (Map.Entry<Pattern, Path> alias : aliases.entrySet()) {
            Matcher matcher = alias.getKey().matcher(relativePath);
            if (matcher.matches()) {
                Path base = alias.getValue();
                String rel = matcher.group(1);
                log.info("Found URL {} to alias {} with target {} and relative path {}",
                    relativePath, alias.getKey(), base, rel);
                return resolve(base, rel);
            }
        }
        return resolve(documentRoot, relativePath);
    }

    /**
     * Resolve relative path with base path.
     *
     * @param base     base directory
     * @param relative relative path
     * @return resolved and normalized path
     */
    private Path resolve(Path base, String relative) {
        // Be careful that the given relative path is not an absolute path,
        // because path.resolve() would trivially return it and we would
        // break out of the document root.
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path resolved = base.resolve(relative);
        return resolved.normalize();
    }

    /**
     * Cache validation: For now we just compare the if-modified-since header from the request with the lastUpdate value (which updated whenever there was an update)
     *
     * @param file    precondition : call verifiyFile(...) before this to make sure the file exists
     * @param request
     * @return
     */
    private boolean fileNotModified(File file, Request request) {
        //get if-last-modified date from request
        String reqLastModifiedString = request.getHeader(HTTP_IF_MODIFIED_SINCE_HEADER);
        if (reqLastModifiedString == null || reqLastModifiedString.equals("")) //if there was no such header, the file should be send again
            return false;
        ZonedDateTime reqLastModifiedDate = ZonedDateTime.parse(reqLastModifiedString, HTTP_DATE_FORMAT);

        //get the date the file was last changed
        ZonedDateTime lastUpdateFile = getFileLastModified(file); //NOSONAR
        //if the file was changed after the requested last modified date (last modified date known to the UA)

        if (lastUpdateFile == null) {
            return false;
        }

        boolean result = !lastUpdateFile.isAfter(reqLastModifiedDate);

        log.debug("if-modified-since: {} lastUpdate: {} -> {}", reqLastModifiedDate, lastUpdateFile, result);

        return result;
    }

    /**
     * Get a ZonedDateTime object that represents when the file was last updated in GMT zone
     *
     * @param file
     * @return
     */
    private ZonedDateTime getFileLastModified(File file) {
        if (file != null) {
            long secondsSince1970 = file.lastModified();
            Date date = new Date(secondsSince1970);
            return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("GMT"));
        }
        return null;
    }

    /**
     * Forces caching of all website files (HTML,CSS,JS,Images,...) for a certain amount of seconds;
     * set the headers HTTP_CACHE_HEADER, HTTP_EXPIRES_HEADER, HTTP_LAST_MODIFIED_HEADER and HTTP_DATE_HEADER
     */
    private void addCachingOfFiles(Response response, File requestedFile) {
        //add Cache-Control header
        response.addHeader(HTTP_CACHE_HEADER, "private,max-age=" + cacheElementsForSeconds);

        //calculate and add expires (and date) header -> format : Tue, 16 Feb 2016 11:51:42 GMT
        ZonedDateTime expireDate = ZonedDateTime.now(ZoneId.of("GMT"));
        String nowGMT = expireDate.format(HTTP_DATE_FORMAT);
        expireDate = expireDate.plusSeconds(cacheElementsForSeconds);
        String expiresString = expireDate.format(HTTP_DATE_FORMAT);

        //add Date (now) header
        response.addHeader(HTTP_DATE_HEADER, nowGMT);
        //add Expires header
        response.addHeader(HTTP_EXPIRES_HEADER, expiresString);

        //add ETag header (hashcode of filename)

        //add Last-Modified Header
        ZonedDateTime lastUpdate = getFileLastModified(requestedFile);

        response.addHeader(HTTP_LAST_MODIFIED_HEADER, lastUpdate.format(HTTP_DATE_FORMAT)); //NOSONAR: lastUpdate could not be null in this case
    }

    private void verifyFile(Path path) {
        File file = path.toFile();

        if (!file.exists()) {
            log.error("File not found (404): {}", path);
            throw new NotFoundException("File not found: " + path);
        }
    }

    /**
     * TODO: Maybe we should use java.activation.MimetypesFileTypeMap?
     *
     * @param path
     * @return
     */
    private String getContentType(Path path) {
        String s = path.toString();
        if (s.endsWith(".png"))
            return "image/png";
        if (s.endsWith(".html"))
            return ContentType.HTML;
        if (s.endsWith(".js"))
            return ContentType.JAVASCRIPT;
        if (s.endsWith(".css"))
            return ContentType.CSS;
        if (s.endsWith(".svg"))
            return "image/svg+xml";
        return "application/octet-stream";
    }

}
