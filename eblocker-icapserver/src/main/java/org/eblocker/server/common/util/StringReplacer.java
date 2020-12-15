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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Faster string replacement for multiple replacements than using a loop and {@code String.replace} or Apache Commons {@code StringUtils.replace}
 * <p>
 * Internally it builds a trie of search strings and iterates input characters stopping at the next match.
 * <p>
 * Example usage:
 * <pre>
 *     new StringReplacer()
 *         .add("first", "1st")
 *         .add("second", "2nd")
 *         .add("third", "3rd")
 *         .replace("first second third")
 * </pre>
 */
public class StringReplacer {

    private final Node root = new Node('\0', null);
    private int minDepth = Integer.MAX_VALUE;
    private int maxDepth = 0;

    public StringReplacer add(String target, String replacement) {
        Node previous = root;
        for (int i = 0; i < target.length(); ++i) {
            char c = target.charAt(i);

            Node next = null;
            if (previous.next != null) {
                for (Node node : previous.next) {
                    if (node.c == c) {
                        next = node;
                        break;
                    }
                }
            }

            if (next != null) {
                previous = next;
            } else {
                Node newNode;
                if (i + 1 < target.length()) {
                    newNode = new Node(c, null);
                } else {
                    newNode = new Node(c, replacement);
                }
                previous.addNext(newNode);
                previous = newNode;
            }
        }

        if (target.length() > maxDepth) {
            maxDepth = target.length();
        }

        if (target.length() < minDepth) {
            minDepth = target.length();
        }

        return this;
    }

    public Match find(CharSequence s) {
        return find(0, s);
    }

    public Match find(int index, CharSequence s) {
        if (root.next == null) {
            return null;
        }

        char[] target = new char[maxDepth];

        for (int i = index; i <= s.length() - minDepth; ++i) {
            Node previous = root;
            for (int j = 0; j < maxDepth; ++j) {
                Node next = null;
                for (Node node : previous.next) {
                    if (node.c == s.charAt(i + j)) {
                        target[j] = node.c;
                        if (node.replacement != null) {
                            return new Match(i, i + j + 1, new String(target, 0, j + 1), node.replacement);
                        }
                        next = node;
                    }
                }
                if (next != null) {
                    previous = next;
                } else {
                    break;
                }
            }
        }
        return null;
    }

    public List<Match> findAll(CharSequence s) {
        List<Match> matches = new ArrayList<>();
        Match match;
        int index = 0;
        while ((match = find(index, s)) != null) {
            matches.add(match);
            index = match.getEndIndex();
        }
        return matches;
    }

    public String replace(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        while (index < s.length()) {
            Match match = find(index, s);
            if (match == null) {
                sb.append(s.subSequence(index, s.length()));
                break;
            }
            sb.append(s.subSequence(index, match.startIndex));
            sb.append(match.replacement);
            index = match.endIndex;
        }
        return sb.toString();
    }

    public static class Match {
        private int startIndex;
        private int endIndex;
        private String target;
        private String replacement;

        private Match(int startIndex, int endIndex, String target, String replacement) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.target = target;
            this.replacement = replacement;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public int getEndIndex() {
            return endIndex;
        }

        public String getTarget() {
            return target;
        }

        public String getReplacement() {
            return replacement;
        }
    }

    private static class Node {
        char c;
        String replacement;
        Node[] next;

        Node(char c, String replacement) {
            this.c = c;
            this.replacement = replacement;
        }

        void addNext(Node node) {
            if (next == null) {
                next = new Node[1];
            } else {
                next = Arrays.copyOf(next, next.length + 1);
            }
            next[next.length - 1] = node;
        }
    }
}
