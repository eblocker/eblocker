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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SSLWhitelistUrl {
    public static final String DOMAIN_NAME_SEPARATOR = "_";

    private static String regex = "^(?:https?://)?(?:[^@/\\n]+@)?([^:/\\n]+)";
    private static Pattern pattern = Pattern.compile(regex);

    private String name;
    private String url;

    public SSLWhitelistUrl(@JsonProperty("name") String name, @JsonProperty("url") String url){
        this.name = name;
        // The URL might contain protocol, path, file extension and much more. Use a regular expression

        Matcher matcher = pattern.matcher(url);
        if (matcher.find()){
        	this.url = matcher.group(1);
		} else {
			this.url = url;
		}
    }

    public void setName(String name){
        this.name = name;
    }

    public String getName(){
        return name;
    }

    public void setUrl(String url){
        this.url = url;
    }

    public String getUrl(){
        return url;
    }


    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        SSLWhitelistUrl that = (SSLWhitelistUrl) other;

        return Objects.equals(this.name, that.name) && Objects.equals(this.url, that.url);
    }

    @Override
    public int hashCode(){
        return Objects.hash(name, url);
    }

    @Override
    public String toString(){
        return getUrl() + DOMAIN_NAME_SEPARATOR + getName();
    }

    public static SSLWhitelistUrl fromString(String string) {
	    String[] parts = string.split(SSLWhitelistUrl.DOMAIN_NAME_SEPARATOR);
	    if (parts.length == 2) {
	    	return new SSLWhitelistUrl(parts[1], parts[0]);
	    } else {
	    	return null;
	    }
    }
}
