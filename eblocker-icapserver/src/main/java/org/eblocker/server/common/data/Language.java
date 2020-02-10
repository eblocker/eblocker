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

/** Simple container class for mapping the language abbrevations (= id -> e.g. 'en' or 'de') to an english name of this language
 */
public class Language {

    private final String id;//abbrevation
    private final String name;

    public Language(String id, String name){
        this.id = id;
        this.name = name;
    }

    @JsonProperty
    public String getId(){
        return id;
    }

    @JsonProperty
    public String getName(){
        return name;
    }

    //-------------------------------------------------------------

    @Override
    public int hashCode(){
        return id.hashCode();
    }

    @Override
    public boolean equals(Object object){
        return (object instanceof Language && ((Language) object).getId() == this.id);
    }

}
