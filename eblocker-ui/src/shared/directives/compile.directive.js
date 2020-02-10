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
export default function CompileDirective($compile) {
    'ngInject';
    /*
     * Dynamically renders the dashboard cards:
     * https://stackoverflow.com/questions/17417607/angular-ng-bind-html-and-directive-within-it
     */
    return function(scope, element, attrs) {
        const ensureCompileRunsOnce = scope.$watch(
            function(scope) {
                // watch the 'compile' expression for changes
                return scope.$eval(attrs.compile);
            },
            function(value) {
                let cardComponentHtml = value.html;

                // when the 'compile' expression changes (see $watch expression) assign it into the current DOM
                element.html(cardComponentHtml + ''); // needs string to compile

                // compile the new DOM and link it to the current scope.
                // NOTE: we only compile .childNodes, so that we don't get into infinite loop compiling ourselves
                $compile(element.contents())(scope);

                // Use un-watch feature to ensure compilation happens only once.
                ensureCompileRunsOnce();
            }
        );
    };
}
