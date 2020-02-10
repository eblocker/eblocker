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
package org.eblocker.server.icap.filter.csv;

import org.eblocker.server.common.transaction.Decision;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.icap.filter.Filter;
import org.eblocker.server.icap.filter.TestContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CSVLineParserTest {
	private final static Logger log = LoggerFactory.getLogger(CSVLineParserTest.class);

	// 0    1        2      3               4                 5                       6                       7
	// TYPE PRIORITY DOMAIN REGEX/MATCHTYPE PATTERN/SUBSTRING REFERRERDOMAINWHITELIST REFERRERDOMAINBLACKLIST REDIRECTPARAM

	@Test
	public void test() {
		assertNotNull(createFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\t-\t-"));
		
		assertNull(createFilter(null));
		assertNull(createFilter(""));
		assertNull(createFilter("0\t1\t2\t3\t4\t5\t6"));
		assertNull(createFilter("#BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\t-\t-"));
		assertNull(createFilter("xLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\t-\t-"));
		assertNull(createFilter("BLOCK\txEDIUM\t-\tCONTAINS\tfoo\t-\t-\t-"));
		assertNull(createFilter("BLOCK\tMEDIUM\t-\txONTAINS\tfoo\t-\t-\t-"));
		assertNull(createFilter("BLOCK\tMEDIUM\t-\tREGEX\t(x]\t-\t-\t-"));
		
		// No white list, no black list --> filter is executed
		assertFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\t-\t-", "bar-foo-bar", "http://www.example.com", Decision.BLOCK);
		assertFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\t-\t-", "bar-bar-bar", "http://www.example.com", Decision.NO_DECISION);
		
		// Referrer is on white list --> filter is executed
		assertFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\texample.com\t-\t-", "bar-foo-bar", "http://www.example.com", Decision.BLOCK);
		
		// Referrer is not on white list --> filter is not executed
		assertFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\texample.com\t-\t-", "bar-foo-bar", "http://www.example1.com", Decision.NO_DECISION);
		
		// Referrer is on black list --> filter is not executed
		assertFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\texample.com\t-", "bar-foo-bar", "http://www.example.com", Decision.NO_DECISION);
		
		// Referrer is not on black list --> filter is executed
		assertFilter("BLOCK\tMEDIUM\t-\tCONTAINS\tfoo\t-\texample.com\t-", "bar-foo-bar", "http://www.example1.com", Decision.BLOCK);
		
		assertFilter("BLOCK\tMEDIUM\t-\tSTARTSWITH\tfoo\t-\t-\t-", "foo-foo-bar", "http://www.example.com", Decision.BLOCK);
		assertFilter("BLOCK\tMEDIUM\t-\tSTARTSWITH\tfoo\t-\t-\t-", "bar-bar-bar", "http://www.example.com", Decision.NO_DECISION);
		
		assertFilter("BLOCK\tMEDIUM\t-\tENDSWITH\tfoo\t-\t-\t-", "foo-foo-foo", "http://www.example.com", Decision.BLOCK);
		assertFilter("BLOCK\tMEDIUM\t-\tENDSWITH\tfoo\t-\t-\t-", "bar-bar-bar", "http://www.example.com", Decision.NO_DECISION);
		
		assertFilter("BLOCK\tMEDIUM\t-\tEQUALS\tfoo\t-\t-\t-", "foo", "http://www.example.com", Decision.BLOCK);
		assertFilter("BLOCK\tMEDIUM\t-\tEQUALS\tfoo\t-\t-\t-", "bar", "http://www.example.com", Decision.NO_DECISION);
		
		assertFilter("BLOCK\tMEDIUM\t-\tREGEX\t.*foo[a-z].*\t-\t-\t-", "---foox---", "http://www.example.com", Decision.BLOCK);
		assertFilter("BLOCK\tMEDIUM\t-\tREGEX\t-*foo[a-z].*\t-\t-\t-", "---fooX---", "http://www.example.com", Decision.NO_DECISION);
	}
	
	@Test
	public void test2() {
		String definition = "ASK	MEDIUM	-	REGEX	https?://[^:/]+\\.google\\.[^:/]+(:|/|$).*	-	-	url";
		String url = "http://www.google.de/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&ved=0CCEQFjAA&url=http%3A%2F%2Fwww.trustcenter.de%2F&ei=brfpVID7LKTpywPb7oLwCQ&usg=AFQjCNE239d1NkWZcECxqLe9akXtrfgNlA";
		String referrer = "http://www.example.com";
		Decision decision = Decision.ASK;
		
		assertFilter(definition, url, referrer, decision);		
	}

	//http://www.econda-monitor.de/l/000012c0/t/cc04f5d9-7bdf-4f70-b723-99ad5200fe72?v=4&emrid=AUvV8SwiT6y*CW3uTp1mOCs0DwHaNuW9&emsid=AUvV8FjWUAIwFpm_ZQ*rnMB6UI*xDpSu&emvid=AUvV8FjWUAIwFpm_ZQ*rnMB6UI*xDpSu&emnc=1&emtn=1&emhost=www.billiger.de&d=eyJwYWdlSWQiOiJJMXR5TjN5R1M4VW5vMTU5WndDckFzIiwiZW50cnlwYWdlIjoiUmVkaXJlY3QvY21vZHVsIiwiTGVhZEV2ZW50IjpbWyJjbGljayIsIjUyNzI4NTE5MiIsIlVFNDBINTAwMCAoRVUtTW9kZWxsKSIsIjIwNjAiLCJVbnRlcmhhbHR1bmdzZWxla3Ryb25pay9UVi9GZXJuc2VoZXIvTENELUZlcm5zZWhlciIsIlNhbXN1bmciLCI5NjA1IiwicmVhbCwtIE9ubGluZXNob3AiLCIyNzkuMDAiLCJydDlSV2NPdmg2QlcwOWdfWWgzRjVzIiwiTlVMTCIsIk5VTEwiLCJOVUxMIl1dLCJUYXJnZXQiOltbImNsaWNrb3V0IiwibGVhZCIsMSwiZCJdXSwiY2dyb3VwIjpbW11dLCJubHNvdXJjZSI6W1tdXSwic2l0ZWlkIjoid3d3LmJpbGxpZ2VyLmRlIiwiY29udGVudCI6IlJlZGlyZWN0L2Ntb2R1bCIsInN3c2giOiIxOTIweDEwODAiLCJ0eiI6LTEsInJlZiI6Imh0dHA6Ly93d3cuYmlsbGlnZXIuZGUvdGhlbWEvNDAtWm9sbC1UViIsInNvdXJjZSI6ImludGVybiIsImVtb3NWIjoiY200OS40In2eMw";

	private Filter createFilter(String definition) {
		return new CSVLineParser().parseLine(definition);
	}
	
	private void assertFilter(String definition, String url, String referrer, Decision decision) {
		Filter filter = new CSVLineParser().parseLine(definition);
		log.info("Created filter {}", filter);
		TransactionContext context = new TestContext(url, referrer, null);
		assertEquals(decision, filter.filter(context).getDecision());
	}

}
