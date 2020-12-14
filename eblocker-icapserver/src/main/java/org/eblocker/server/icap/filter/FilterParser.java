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
package org.eblocker.server.icap.filter;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FilterParser {

    private final Supplier<FilterLineParser> lineParserSupplier;

    public FilterParser(Supplier<FilterLineParser> lineParserSupplier) {
        this.lineParserSupplier = lineParserSupplier;
    }

    public List<Filter> parse(InputStream in) throws IOException {
        return IOUtils.readLines(in).stream()
                .map(line -> lineParserSupplier.get().parseLine(line))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
