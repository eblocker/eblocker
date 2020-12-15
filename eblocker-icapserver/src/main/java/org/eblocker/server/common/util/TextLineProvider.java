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
package org.eblocker.server.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

/*
 * This class is to be later replaced by ScriptRunnerOutput
 */
public class TextLineProvider {
    private static final Logger logger = LoggerFactory.getLogger(TextLineProvider.class);

    public List<String> getLines(String filename) {
        List<String> lines = new Vector<>();
        try (BufferedReader buffReader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = buffReader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            logger.error("Error on parsing file.", e);
        }
        return lines;
    }
}
