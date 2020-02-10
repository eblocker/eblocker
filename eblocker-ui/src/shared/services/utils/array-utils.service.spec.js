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
describe('Array utils service', function() { // jshint ignore: line

    let utils;

    beforeEach(angular.mock.module('eblocker.dashboard'));

    beforeEach(inject(function(_ArrayUtilsService_, _$httpBackend_) {
        utils = _ArrayUtilsService_;
    }));

    describe('test sortByPropertyName', function() {
        it('order a list by name property', function() {
            const el1 = {name: 'Camel'};
            const el2 = {name: 'Ape'};
            const el3 = {name: 'Elephant'};
            const el4 = {name: 'Donkey'};
            const el5 = {name: 'Beaver'};
            const unsorted = [el1, el2, el3, el4, el5];
            const sorted = utils.sortByPropertyName(unsorted);
            expect(sorted[0].name).toMatch(el2.name);
            expect(sorted[1].name).toMatch(el5.name);
            expect(sorted[2].name).toMatch(el1.name);
            expect(sorted[3].name).toMatch(el4.name);
            expect(sorted[4].name).toMatch(el3.name);
        });

        it('order a list by name property and ignore case', function() {
            const el1 = {name: 'camel'};
            const el2 = {name: 'Ape'};
            const el3 = {name: 'Elephant'};
            const el4 = {name: 'donkey'};
            const el5 = {name: 'Beaver'};
            const unsorted = [el1, el2, el3, el4, el5];
            const sorted = utils.sortByPropertyName(unsorted);
            expect(sorted[0].name).toMatch(el2.name);
            expect(sorted[1].name).toMatch(el5.name);
            expect(sorted[2].name).toMatch(el1.name);
            expect(sorted[3].name).toMatch(el4.name);
            expect(sorted[4].name).toMatch(el3.name);
        });
    });

    describe('test sortByProperty', function() {
        it('order a list by a variable property', function() {
            const el1 = {animal: 'Camel'};
            const el2 = {animal: 'Ape'};
            const el3 = {animal: 'Elephant'};
            const el4 = {animal: 'Donkey'};
            const el5 = {animal: 'Beaver'};
            const unsorted = [el1, el2, el3, el4, el5];
            const sorted = utils.sortByProperty(unsorted, 'animal');
            expect(sorted[0].animal).toMatch(el2.animal);
            expect(sorted[1].animal).toMatch(el5.animal);
            expect(sorted[2].animal).toMatch(el1.animal);
            expect(sorted[3].animal).toMatch(el4.animal);
            expect(sorted[4].animal).toMatch(el3.animal);
        });

        it('order a list by a variable property and ignore case', function() {
            const el1 = {animal: 'camel'};
            const el2 = {animal: 'Ape'};
            const el3 = {animal: 'Elephant'};
            const el4 = {animal: 'donkey'};
            const el5 = {animal: 'Beaver'};
            const unsorted = [el1, el2, el3, el4, el5];
            const sorted = utils.sortByProperty(unsorted, 'animal');
            expect(sorted[0].animal).toMatch(el2.animal);
            expect(sorted[1].animal).toMatch(el5.animal);
            expect(sorted[2].animal).toMatch(el1.animal);
            expect(sorted[3].animal).toMatch(el4.animal);
            expect(sorted[4].animal).toMatch(el3.animal);
        });
    });

    describe('test sortByPropertyWithFallback', function() {
        let el1, el2, el3, el4, el5, el6, list;
        beforeEach(function() {
            el1 = {surname: 'Alpha', firstname: 'ceasar', age: 35};
            el2 = {surname: 'alpha', firstname: 'Berta', age: 25};
            el3 = {surname: 'beta', firstname: 'anton', age: 45};
            el4 = {surname: 'beta', firstname: 'Dieter', age: 35};
            el5 = {surname: 'gamma', firstname: 'gertrud', age: 25};
            el6 = {surname: 'Gamma', firstname: 'Agnes', age: 45};
            list = [el3, el1, el6, el5, el2, el4];
        });

        it('should sort surname and if equal by first name', function() {
            const sorted = utils.sortByPropertyWithFallback(list, 'surname', 'firstname');
            expect(sorted[0].surname).toMatch(el2.surname);
            expect(sorted[1].surname).toMatch(el1.surname);
            expect(sorted[2].surname).toMatch(el3.surname);
            expect(sorted[3].surname).toMatch(el4.surname);
            expect(sorted[4].surname).toMatch(el6.surname);
            expect(sorted[5].surname).toMatch(el5.surname);
        });

        it('should sort surname and if equal by age', function() {
            const sorted = utils.sortByPropertyWithFallback(list, 'surname', 'age');
            expect(sorted[0].surname).toMatch(el2.surname);
            expect(sorted[1].surname).toMatch(el1.surname);
            expect(sorted[2].surname).toMatch(el4.surname);
            expect(sorted[3].surname).toMatch(el3.surname);
            expect(sorted[4].surname).toMatch(el5.surname);
            expect(sorted[5].surname).toMatch(el6.surname);
        });

        it('should sort age and if equal by first name', function() {
            const sorted = utils.sortByPropertyWithFallback(list, 'age', 'firstname');
            expect(sorted[0].surname).toMatch(el2.surname);
            expect(sorted[1].surname).toMatch(el5.surname);
            expect(sorted[2].surname).toMatch(el1.surname);
            expect(sorted[3].surname).toMatch(el4.surname);
            expect(sorted[4].surname).toMatch(el6.surname);
            expect(sorted[5].surname).toMatch(el3.surname);
        });
    });

    describe('test containsByProperty', function() {
        it('return true if an element is contained in the list', function() {
            const list = [{domain: 'a test'}, {domain: 'b test'}, {domain: 'c test'}, {domain: 'd test'}];
            const el = {domain: 'b test'};
            expect(utils.containsByProperty(list, 'domain', el.domain)).toBe(true);
        });
        it('return false if an element is contained in the list', function() {
            const list = [{domain: 'a test'}, {domain: 'b test'}, {domain: 'c test'}, {domain: 'd test'}];
            const el = {domain: 'f test'};
            expect(utils.containsByProperty(list, 'domain', el.domain)).toBe(false);
        });
    });

    describe('test contains', function() {
        it('return true if an element is contained in the list', function() {
            const list = ['other', 'domain', 'test'];
            expect(utils.containsByProperty(list, 'domain')).toBe(true);
        });
        it('return false if an element is contained in the list', function() {
            const list = ['other', 'no-domain', 'test'];
            expect(utils.contains(list, 'domain')).toBe(false);
        });
    });

    describe('test getItemBy', function() {
        it('should return false if list is empty', function() {
            const list = [];
            const value = 1;
            expect(utils.getItemBy(list, 'id', value)).toBe(false);
        });
        it('should return false if value is undefined', function() {
            const list = [{id: 1}];
            expect(utils.getItemBy(list, 'id', undefined)).toBe(false);
        });
        it('should return false if list is empty and value is undefined', function() {
            const list = [];
            expect(utils.getItemBy(list, 'id', undefined)).toBe(false);
        });
        it('should return false if list has only one element and the property does not match the value', function() {
            const list = [{id: 2}];
            const value = 1;
            expect(utils.getItemBy(list, 'id', value)).toBe(false);
        });
        it('should return false if list many elements and no property matches the value', function() {
            const list = [{id: 2}, {id: 3}, {id: 4}, {id: 5}, {id: 6}];
            const value = 1;
            expect(utils.getItemBy(list, 'id', value)).toBe(false);
        });
        it('should return element if list has only one element and the property matches the value', function() {
            const list = [{id: 1}];
            const value = 1;
            const ret = utils.getItemBy(list, 'id', value);
            expect(angular.isObject(ret)).toBe(true);
            expect(ret.id).toEqual(value);
        });
        it('should return element if list has many elements and the property of one element matches the value',
            function() {
            const list = [{id: 2}, {id: 3}, {id: 1}, {id: 4}, {id: 5}, {id: 6}];
            const value = 1;
            const ret = utils.getItemBy(list, 'id', value);
            expect(angular.isObject(ret)).toBe(true);
            expect(ret.id).toEqual(value);
        });
    });
});
