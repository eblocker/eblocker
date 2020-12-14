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
package org.eblocker.server.http.service;

import org.eblocker.registration.ProductFeature;
import org.eblocker.registration.ProductInfo;
import org.eblocker.server.common.data.DataSource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ProductInfoServiceTest {

    private final static ProductInfo PRO = new ProductInfo(
            "product-id-pro",
            "product-name-pro",
            new String[]{ "PRO" }
    );

    private final static ProductInfo FAM = new ProductInfo(
            "product-id-fam",
            "product-name-fam",
            new String[]{ "FAM" }
    );

    private final static ProductInfo NONE = new ProductInfo(
            "product-id-none",
            "product-name-none",
            new String[]{}
    );

    private final static ProductInfo DEMO = new ProductInfo(
            "product-id-demo",
            "product-name-demo",
            new String[]{ "EVL_BAS", "EVL_PRO", "EVL_FAM", "BAS", "PRO", "FAM" }
    );

    private final static ProductInfo NULL = null;

    @Test
    public void testGet() {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.get(ProductInfo.class, ProductInfoService.KEY)).thenReturn(PRO);

        ProductInfoService productInfoService = new ProductInfoService(dataSource);
        productInfoService.init();
        ProductInfo productInfo = productInfoService.get();
        assertEquals(PRO.getProductId(), productInfo.getProductId());

        verify(dataSource).get(ProductInfo.class, ProductInfoService.KEY);
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    public void testDelete() {
        DataSource dataSource = mock(DataSource.class);

        ProductInfoService productInfoService = new ProductInfoService(dataSource);
        productInfoService.init();
        productInfoService.clear();

        verify(dataSource).get(ProductInfo.class, ProductInfoService.KEY);
        verify(dataSource).delete(ProductInfo.class, ProductInfoService.KEY);
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    public void testSave() {
        DataSource dataSource = mock(DataSource.class);

        ProductInfoService productInfoService = new ProductInfoService(dataSource);
        productInfoService.init();
        productInfoService.save(PRO);

        verify(dataSource).get(ProductInfo.class, ProductInfoService.KEY);
        verify(dataSource).save(PRO, ProductInfoService.KEY);
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    public void testHasFeature_OK_FAM() {
        doTestHasFeature(FAM, true);
    }

    @Test
    public void testHasFeature_OK_DEMO() {
        doTestHasFeature(DEMO, true);
    }

    @Test
    public void testHasFeature_NOK() {
        doTestHasFeature(PRO, false);
    }

    @Test
    public void testHasFeature_NOK_NONE() {
        doTestHasFeature(NONE, false);
    }

    @Test
    public void testHasFeature_NOK_NULL() {
        doTestHasFeature(NULL, false);
    }

    public void doTestHasFeature(ProductInfo productInfo, boolean expected) {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.get(ProductInfo.class, ProductInfoService.KEY)).thenReturn(productInfo);

        ProductInfoService productInfoService = new ProductInfoService(dataSource);
        productInfoService.init();
        assertEquals(expected, productInfoService.hasFeature(ProductFeature.FAM));

        verify(dataSource).get(ProductInfo.class, ProductInfoService.KEY);
        verifyNoMoreInteractions(dataSource);
    }
}
