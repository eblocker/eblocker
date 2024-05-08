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
package org.eblocker.server.icap.resources;

import java.nio.charset.Charset;

public class SimpleResource implements EblockerResource {

    private final String name;
    private final String path;
    private final Charset charset;

    public SimpleResource(String path) {
        this.name = path;
        this.path = path;
        this.charset = Charset.forName("UTF-8");
    }

    public SimpleResource(String name, String path) {
        this.name = name;
        this.path = path;
        this.charset = Charset.forName("UTF-8");
    }

    public SimpleResource(String name, String path, Charset charset) {
        this.name = name;
        this.path = path;
        this.charset = charset;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public String toString() {
        return "SimpleResource{path='" + path + "'}";
    }
}
