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

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ResourceLoaderTest {
	
	private static final Charset CHARSET_UTF8 = Charset.forName("UTF-8");
	private static final Charset CHARSET_LATIN1 = Charset.forName("ISO-8859-1");

	@Test
	public void testLoadClassPathResource() {
		String page = ResourceHandler.load(new SimpleResource("test-data/sample.xhtml"));
		assertNotNull(page);
		assertTrue(page.contains("Hello World"));
		
		page = ResourceHandler.load(new SimpleResource("classpath:test-data/sample.xhtml"));
		assertNotNull(page);
		assertTrue(page.contains("Hello World"));
	}

	@Test
	public void testLoadFileResource() throws IOException {
		Path temp = Files.createTempFile("test", ".txt");
		List<String> lines = Arrays.asList("Hello World", "Hallo Welt");
		Files.write(temp,  lines, CHARSET_UTF8);
		
		String page = ResourceHandler.load(new SimpleResource("file:"+temp.toString()));
		assertNotNull(page);
		assertTrue(page.contains("Hello World"));
		assertTrue(page.contains("Hallo Welt"));
		
		page = ResourceHandler.load(new SimpleResource(temp.toString()));
		assertNotNull(page);
		assertTrue(page.contains("Hello World"));
		assertTrue(page.contains("Hallo Welt"));
	}

	@Test
	public void testLoadFileResource_withCharset() throws IOException {
		Path temp = Files.createTempFile("test", ".txt");
		List<String> lines = Arrays.asList("Hällö Wörld");
		Files.write(temp,  lines, CHARSET_UTF8);
		
		String page = ResourceHandler.load(new SimpleResource("SAMPLE", "file:"+temp.toString(), CHARSET_LATIN1));
		assertNotNull(page);
		assertFalse(page.contains("Hällö Wörld"));
		
		page = ResourceHandler.load(new SimpleResource("SAMPLE", "file:"+temp.toString(), CHARSET_UTF8));
		assertNotNull(page);
		assertTrue(page.contains("Hällö Wörld"));
		
		temp = Files.createTempFile("test", ".txt");
		lines = Arrays.asList("Hällö Wörld");
		Files.write(temp,  lines, CHARSET_LATIN1);
		
		page = ResourceHandler.load(new SimpleResource("SAMPLE", "file:"+temp.toString(), CHARSET_LATIN1));
		assertNotNull(page);
		assertTrue(page.contains("Hällö Wörld"));
		
		page = ResourceHandler.load(new SimpleResource("SAMPLE", "file:"+temp.toString(), CHARSET_UTF8));
		assertNotNull(page);
		assertFalse(page.contains("Hällö Wörld"));
		
	}

}
