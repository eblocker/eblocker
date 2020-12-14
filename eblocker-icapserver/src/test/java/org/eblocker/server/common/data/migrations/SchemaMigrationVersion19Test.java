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
package org.eblocker.server.common.data.migrations;

import org.eblocker.registration.ProductInfo;
import org.eblocker.server.common.data.DataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SchemaMigrationVersion19Test {

    private SchemaMigrationVersion19 migration;
    private DataSource dataSource;
    private ProductInfo productInfo;
    private String[] features;

    @Before
    public void setup() {
        dataSource = Mockito.mock(DataSource.class);

        migration = new SchemaMigrationVersion19(dataSource);
    }

    @Test
    public void getSourceVersion() throws Exception {
        Assert.assertEquals("18", migration.getSourceVersion());
    }

    @Test
    public void getTargetVersion() throws Exception {
        Assert.assertEquals("19", migration.getTargetVersion());
    }

    @Test
    public void testFamFeatureUserGetsBas() {
        features = new String[]{ "FAM" };
        productInfo = createProductInfo(features);
        Mockito.when(dataSource.get(ProductInfo.class, 0)).thenReturn(productInfo);

        migration.migrate();

        String[] expectedFeatures = new String[]{ "FAM", "BAS" };
        ProductInfo expectedProductInfo = createProductInfo(expectedFeatures);
        Mockito.verify(dataSource).save(Mockito.eq(expectedProductInfo), Mockito.eq(0));
    }

    @Test
    public void testProFeatureUserGetsBas() {
        features = new String[]{ "PRO" };
        productInfo = createProductInfo(features);
        Mockito.when(dataSource.get(ProductInfo.class, 0)).thenReturn(productInfo);

        migration.migrate();

        String[] expectedFeatures = new String[]{ "PRO", "BAS" };
        ProductInfo expectedProductInfo = createProductInfo(expectedFeatures);
        Mockito.verify(dataSource).save(Mockito.eq(expectedProductInfo), Mockito.eq(0));
    }

    @Test
    public void testOtherFeatureUserGetNoBas() {
        features = new String[]{ "Fam", "fam", "Pro", "pro", "FOO", "bar" };
        productInfo = createProductInfo(features);
        Mockito.when(dataSource.get(ProductInfo.class, 0)).thenReturn(productInfo);

        migration.migrate();

        String[] expectedFeatures = new String[]{ "Fam", "fam", "Pro", "pro", "FOO", "bar" };
        ProductInfo expectedProductInfo = createProductInfo(expectedFeatures);
        Mockito.verify(dataSource).save(Mockito.eq(expectedProductInfo), Mockito.eq(0));
    }

    private ProductInfo createProductInfo(String[] features) {
        return new ProductInfo("productInfoProductId", "productInfoProductName", features);
    }

}
