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
export default function ThemingProvider($mdThemingProvider) {
    'ngInject';
    'use strict';

    /*
     * Palette generated with the help of: http://mcg.mbitson.com/
     *
     * Using #ea5a09 as base for the primary palette
     * and #2d3037 as base for the accent palette
     *
     */
    $mdThemingProvider.definePalette('eblockerPrimary', {
        '50': 'fcebe1',
        '100': 'f9ceb5',
        '200': 'f5ad84',
        '300': 'f08c53',
        '400': 'ed732e',
        '500': 'ea5a09', // CI: main color "eBlocker Orange" from primary pallette
        '600': 'e75208',
        '700': 'e44806',
        '800': 'e13f05',
        '900': 'db2e02',
        'A100': 'ffffff',
        'A200': 'ffd6cf',
        'A400': 'ffab9c',
        'A700': 'ff9583',
        'contrastDefaultColor': 'light',
        'contrastDarkColors': [
            '50',
            '100',
            '200',
            '300',
            '400',
            'A100',
            'A200',
            'A400',
            'A700'
        ],
        'contrastLightColors': [
            '500',
            '600',
            '700',
            '800',
            '900'
        ]
    });

    $mdThemingProvider.definePalette('eblockerPrimaryGreen', {
        '50': 'fcebe1',
        '100': 'f9ceb5',
        '200': 'f5ad84',
        '300': 'f08c53',
        '400': 'ed732e',
        '500': '00b800', // green darker: *29cb44* / lighter: 2fd140 / from iOs: 4cd964 / from content-ok: 00b800
        '600': 'e75208',
        '700': 'e44806',
        '800': 'e13f05',
        '900': 'db2e02',
        'A100': 'ffffff',
        'A200': 'ffd6cf',
        'A400': 'ffab9c',
        'A700': 'ff9583',
        'contrastDefaultColor': 'light',
        'contrastDarkColors': [
            '50',
            '100',
            '200',
            '300',
            '400',
            'A100',
            'A200',
            'A400',
            'A700'
        ],
        'contrastLightColors': [
            '500',
            '600',
            '700',
            '800',
            '900'
        ]
    });
    $mdThemingProvider.definePalette('eblockerSecondary', {
        '50': 'e6e6e7',
        '100': 'c0c1c3',
        '200': '96989b',
        '300': '6c6e73',
        '400': '4d4f55',
        '500': '2d3037', // CI: main color "dark gray" from secondary palette
        '600': '282b31',
        '700': '22242a',
        '800': '1c1e23',
        '900': '111316',
        'A100': '5cadff',
        'A200': '2994ff',
        'A400': '007af5',
        'A700': '006edb',
        'contrastDefaultColor': 'light',
        'contrastDarkColors': [
            '50',
            '100',
            '200',
            'A100',
            'A200'
        ],
        'contrastLightColors': [
            '300',
            '400',
            '500',
            '600',
            '700',
            '800',
            '900',
            'A400',
            'A700'
        ]
    });

    $mdThemingProvider.definePalette('eBlockerSecondaryBlue', {
        '50': 'eff0f3',
        '100': 'd7dae2',
        '200': 'bcc1cf',
        '300': 'a1a8bb',
        '400': '8d96ad',
        '500': '79839e', // CI: third color "blue" from secondary palette
        '600': '717b96',
        '700': '66708c',
        '800': '5c6682',
        '900': '495370',
        'A100': 'cfdbff',
        'A200': '9cb5ff',
        'A400': '698fff',
        'A700': '507cff',
        'contrastDefaultColor': 'light',
        'contrastDarkColors': [
            '50',
            '100',
            '200',
            '300',
            '400',
            '500',
            'A100',
            'A200',
            'A400'
        ],
        'contrastLightColors': [
            '600',
            '700',
            '800',
            '900',
            'A700'
        ]
    });

    $mdThemingProvider.theme('eBlockerTheme')
        .primaryPalette('eblockerPrimary')
        .accentPalette('eblockerSecondary');

    $mdThemingProvider.setDefaultTheme('eBlockerTheme');

    $mdThemingProvider.theme('eBlockerThemeSwitch')
        .primaryPalette('eblockerPrimaryGreen')
        .accentPalette('eblockerSecondary');

    $mdThemingProvider.theme('eBlockerThemeRadio')
        .primaryPalette('eblockerPrimaryGreen')
        .accentPalette('eblockerSecondary');

    $mdThemingProvider.theme('eBlockerThemeCheckbox')
        .primaryPalette('eblockerPrimaryGreen');

    $mdThemingProvider.theme('eBlockerThemeInput')
        .primaryPalette('eBlockerSecondaryBlue')
        .accentPalette('eblockerSecondary');


}
