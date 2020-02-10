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
export default function($translateProvider, LANG_FILENAMES) {
    'ngInject';
    //translations
    //register languages; load from .json files

    const options= {
        files:  [{
            prefix: LANG_FILENAMES.dashboard.prefix,
            suffix: LANG_FILENAMES.dashboard.suffix
        }, {
            prefix: LANG_FILENAMES.shared.prefix,
            suffix: LANG_FILENAMES.shared.suffix
        }]
    };

    $translateProvider.useStaticFilesLoader(options);

    // $translateProvider.useStaticFilesLoader({
    //     prefix: '/locale/lang-dashboard-',
    //     suffix: '.json'
    // });

    // Enable MessageFormatInterpolation for pluralisation.
    // Use MessageFormatInterpolation only as optional formatter.
    // See https://angular-translate.github.io/docs/#/guide/14_pluralization#pluralization_the-drawback
    $translateProvider.addInterpolation('$translateMessageFormatInterpolation');

    //register preferred language
    $translateProvider.preferredLanguage('en');

    //sanitizing strategy (escaping)
    $translateProvider.useSanitizeValueStrategy('escapeParameters');
}
