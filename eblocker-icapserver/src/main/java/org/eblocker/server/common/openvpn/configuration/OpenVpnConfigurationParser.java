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
package org.eblocker.server.common.openvpn.configuration;

import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenVpnConfigurationParser {
    private static final char[] COMMENT_CHARACTERS = {';', '#'};

    private static final Pattern TAG_OPEN_PATTERN = Pattern.compile("<([^/]+.*)>");
    private static final Pattern TAG_CLOSE_PATTERN = Pattern.compile("</(.*)>");

    private final Set<String> optionsGroupNames;
    private ListIterator<String> currentLine;

    @Inject
    public OpenVpnConfigurationParser(@Named("openvpn.configuration.options.group") String optionsGroup) {
        this.optionsGroupNames = ResourceHandler.readLinesAsSet(new SimpleResource(optionsGroup));
    }

    public List<Option> parse(String config) throws ParseException {
        List<String> configLines = Arrays.asList(config.split("\n"));
        currentLine = configLines.listIterator();
        return parseOptions(null);
    }

    private List<Option> parseOptions(String closeTag) throws ParseException {
        List<Option> options = new ArrayList<>();
        Option parsedOption;
        while ((parsedOption = parseNextOption(closeTag)) != null) {
            options.add(parsedOption);
        }
        return options;
    }

    private Option parseNextOption(String expectedCloseTag) throws ParseException {
        String line = findNextOptionLine();
        if (line == null) {
            if (expectedCloseTag == null) {
                return null;
            }
            throw new ParseException("missing closing tag: " + expectedCloseTag);
        }

        Matcher tagCloseMatcher = TAG_CLOSE_PATTERN.matcher(line);
        if (tagCloseMatcher.matches()) {
            String name = tagCloseMatcher.group(1);
            if (name.equalsIgnoreCase(expectedCloseTag)) {
                return null;
            }
            throw new ParseException("unexpected close tag: " + name);
        }

        int lineNumber = currentLine.previousIndex() + 1;
        Matcher tagOpenMatcher = TAG_OPEN_PATTERN.matcher(line);
        if (tagOpenMatcher.matches()) {
            String name = tagOpenMatcher.group(1);
            if (!optionsGroupNames.contains(name)) {
                String inlineContent = getInlineContent(name);
                return new InlineOption(lineNumber, name, inlineContent);
            } else {
                List<Option> options = parseOptions(name);
                return new OptionsGroup(lineNumber, name, options);
            }
        } else {
            String[] options = line.split("[ \\t]+");
            if (options.length == 1) {
                return new SimpleOption(lineNumber, options[0]);
            } else {
                // no way to slice array in java :(
                return new SimpleOption(lineNumber, options[0], Arrays.copyOfRange(options, 1, options.length));
            }
        }
    }

    private String findNextOptionLine() {
        while(currentLine.hasNext()) {
            String line = currentLine.next().trim();
            if (!line.isEmpty() && !isComment(line)) {
                return line;
            }
        }

        return null;
    }

    private boolean isComment(String line) {
        char firstChar = line.charAt(0);
        for (char commentCharacter : COMMENT_CHARACTERS) {
            if (firstChar == commentCharacter) {
                return true;
            }
        }
        return false;
    }

    private String getInlineContent(String closingTag) throws ParseException {
        Matcher matcher = TAG_CLOSE_PATTERN.matcher("");
        StringBuilder inlineContent = new StringBuilder();
        while (currentLine.hasNext()) {
            String line = currentLine.next();
            matcher.reset(line.trim());
            if (matcher.matches() && matcher.group(1).equalsIgnoreCase(closingTag)) {
                return inlineContent.toString();
            }
            inlineContent.append(line);
            inlineContent.append('\n');
        }

        throw new ParseException("expected end of inline content not found: " + closingTag);
    }

    public class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }
}
