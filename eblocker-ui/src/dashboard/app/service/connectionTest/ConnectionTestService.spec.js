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
import 'angular-mocks';

describe('App: dashboard; ConnectionTestService', function() { // jshint ignore: line

    beforeEach(angular.mock.module('eblocker.services'));

    let connectionTestService, $httpBackend, $q, $scope;

    describe('initially', function() {
        beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
            connectionTestService = _ConnectionTestService_;
        }));

        it('should create a service instance', function() {
            expect(angular.isDefined(connectionTestService)).toBe(true);
        });
    });

    describe('A connection test', function() { // jshint ignore: line
        beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
            connectionTestService = _ConnectionTestService_;
            
            $httpBackend = _$httpBackend_;
            $scope = _$rootScope_.$new();
            
            $httpBackend.when('GET', 'http://working')
            .respond(function(method, url, data, headers){
                return [200, 'data', {/* headers */}, 'OK'];
            });
            $httpBackend.when('GET', 'http://not-found')
            .respond(function(method, url, data, headers){
                return [404, 'not found', {/* headers */}, 'Not Found'];
            });
            $httpBackend.when('GET', 'http://www.not-available.tld/index.html')
            .respond(function(method, url, data, headers){
                return [-1, '', {/* headers */}, ''];
            });
            
            $q = _$q_;
        }));
        
        it('should handle a delivered page', function(done) {
            const spyObj = {};
            spyObj.success = function(response) {
                // actual test expectations
                expect(response.status).toBe('success');
                expect(response.httpcode).toBe(200);
                expect(response.httpdesc).toBe('OK');
                done();
            };
            spyObj.error = function() {
                // will not be called
            };

            spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
            spyOn(spyObj, 'error'); // jshint ignore: line

            connectionTestService.makeTestRequest('http', 'working', true).then(spyObj.success, spyObj.error);

            $httpBackend.flush();

            // make sure only success has been called
            expect(spyObj.success).toHaveBeenCalled();
            expect(spyObj.error).not.toHaveBeenCalled();
        });

        it ('should handle a page not found', function(done) {
            const spyObj = {};
            spyObj.success = function(response) {
                // actual test expectations
                expect(response.status).toBe('error');
                expect(response.httpcode).toBe(404);
                expect(response.httpdesc).toBe('Not Found');
                done();
            };
            spyObj.error = function() {
                // will not be called
            };

            spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
            spyOn(spyObj, 'error'); // jshint ignore: line

            connectionTestService.makeTestRequest('http', 'not-found', true).then(spyObj.success, spyObj.error);

            $httpBackend.flush();

            // make sure only success has been called
            expect(spyObj.success).toHaveBeenCalled();
            expect(spyObj.error).not.toHaveBeenCalled();
        });

        it ('should not accept any protocol but http(s)', function() {
            const spyObj = {};
            spyObj.success = function() {
                // will not be called
            };
            spyObj.error = function(response) {
                // actual test expectations
                expect(response).toBe('Unsupported protocol specified, expected \'http\' or \'https\'');
            };

            // spy on success and error function to make sure that only error is called
            // This makes sure that our expectations have been tested. Otherwise the code inside the then block
            // may be skipped silently.
            spyOn(spyObj, 'success'); // jshint ignore: line
            spyOn(spyObj, 'error').and.callThrough(); // jshint ignore: line

            // the actual test call
            connectionTestService.makeTestRequest('http23', 'working', true).then(spyObj.success, spyObj.error);

            // trigger digest circle manually to resolve promise
            $scope.$apply();

            // make sure only error has been called
            expect(spyObj.success).not.toHaveBeenCalled();
            expect(spyObj.error).toHaveBeenCalled();
        });

        it ('should handle a network error as such', function(done) {
            const spyObj = {};
            spyObj.success = function(response) {
                // actual test expectations
                expect(response.status).toBe('error');
                expect(response.httpcode).toBe(-1);
                expect(response.httpdesc).toBe('');
                done();
            };
            spyObj.error = function() {
                //will not be called
            };

            spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
            spyOn(spyObj, 'error'); // jshint ignore: line

            // the actual test call
            connectionTestService.makeTestRequest('http', 'www.not-available.tld/index.html', true)
                .then(spyObj.success, spyObj.error);

            $httpBackend.flush();

            // make sure only success has been called
            expect(spyObj.success).toHaveBeenCalled();
            expect(spyObj.error).not.toHaveBeenCalled();
        });
    });

    describe('A pattern blocker test', function() { // jshint ignore: line
        var $httpBackend;

        beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
            connectionTestService = _ConnectionTestService_;

            $httpBackend = _$httpBackend_;
            $scope = _$rootScope_.$new();


            $q = _$q_;
        }));

        it('should handle a 204 (no content)', function(done) {
            $httpBackend.when('GET', 'http://setup.eblocker.com/_check_/pattern-blocker')
            .respond(function(method, url, data, headers){
                return [204, 'data', {/* headers */}, 'No Content'];
            });
            const spyObj = {};
            spyObj.success = function(response) {
                // actual test expectations
                expect(response.checkStatus).toBe('passed');
                expect(response.checkDetails.httpcode).toBe(204);
                expect(response.checkDetails.httpdesc).toBe('No Content');
                done();
            };
            spyObj.error = function() {
                // will not be called
                expect(true).toBe(false);
            };

            spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
            spyOn(spyObj, 'error'); // jshint ignore: line

            connectionTestService.makePatternBlockerTestRequest().then(spyObj.success, spyObj.error);

            $httpBackend.flush();

            // make sure only success has been called
            expect(spyObj.success).toHaveBeenCalled();
            expect(spyObj.error).not.toHaveBeenCalled();
        });

        it('should handle a 404 (not found)', function(done) {
            $httpBackend.when('GET', 'http://setup.eblocker.com/_check_/pattern-blocker')
            .respond(function(method, url, data, headers){
                return [404, 'not found', {/* headers */}, 'Not Found'];
            });
            const spyObj = {};
            spyObj.success = function(response) {
                // actual test expectations
                expect(response.checkStatus).toBe('failed');
                expect(response.checkDetails.httpcode).toBe(404);
                expect(response.checkDetails.httpdesc).toBe('Not Found');
                done();
            };
            spyObj.error = function() {
                // will not be called
                expect(true).toBe(false);
            };

            spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
            spyOn(spyObj, 'error'); // jshint ignore: line

            connectionTestService.makePatternBlockerTestRequest().then(spyObj.success, spyObj.error);

            $httpBackend.flush();

            // make sure only success has been called
            expect(spyObj.success).toHaveBeenCalled();
            expect(spyObj.error).not.toHaveBeenCalled();
        });

        it('should handle a network error as such', function(done) {
            $httpBackend.when('GET', 'http://setup.eblocker.com/_check_/pattern-blocker')
            .respond(function(method, url, data, headers){
                return [-1, '', {/* headers */}, ''];
            });
            const spyObj = {};
            spyObj.success = function(response) {
                // actual test expectations
                expect(response.checkStatus).toBe('failed');
                expect(response.checkDetails.httpcode).toBe(-1);
                expect(response.checkDetails.httpdesc).toBe('');
                done();
            };
            spyObj.error = function() {
                //will not be called
                expect(true).toBe(false);
            };

            spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
            spyOn(spyObj, 'error'); // jshint ignore: line

            // the actual test call
            connectionTestService.makePatternBlockerTestRequest().then(spyObj.success, spyObj.error);

            $httpBackend.flush();

            // make sure only success has been called
            expect(spyObj.success).toHaveBeenCalled();
            expect(spyObj.error).not.toHaveBeenCalled();
        });
    });

    // Testing for HTTP and HTTPS is nearly identical
    httpsTest('http');
    httpsTest('https');
    function httpsTest(protocol) {
        describe('A ' + protocol + ' routing test', function() { // jshint ignore: line
            var $httpBackend;

            beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
                connectionTestService = _ConnectionTestService_;

                $httpBackend = _$httpBackend_;
                $scope = _$rootScope_.$new();


                $q = _$q_;
            }));

            it('should handle a 204 (no content)', function(done) {
                $httpBackend.when('GET', protocol + '://setup.eblocker.com/_check_/routing')
                .respond(function(method, url, data, headers){
                    return [204, 'data', {/* headers */}, 'No Content'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('passed');
                    expect(response.checkDetails.httpcode).toBe(204);
                    expect(response.checkDetails.httpdesc).toBe('No Content');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeHttpRoutingTestRequest(protocol).then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a 404 (not found)', function(done) {
                $httpBackend.when('GET', protocol + '://setup.eblocker.com/_check_/routing')
                .respond(function(method, url, data, headers){
                    return [404, 'not found', {/* headers */}, 'Not Found'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(404);
                    expect(response.checkDetails.httpdesc).toBe('Not Found');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeHttpRoutingTestRequest(protocol).then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a network error as such', function(done) {
                $httpBackend.when('GET', protocol + '://setup.eblocker.com/_check_/routing')
                .respond(function(method, url, data, headers){
                    return [-1, '', {/* headers */}, ''];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(-1);
                    expect(response.checkDetails.httpdesc).toBe('');
                    done();
                };
                spyObj.error = function() {
                    //will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                // the actual test call
                connectionTestService.makeHttpRoutingTestRequest(protocol).then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });
      });

        describe('A domain blocker test (ads-domains)', function() { // jshint ignore: line
            var $httpBackend;

            beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
                connectionTestService = _ConnectionTestService_;

                $httpBackend = _$httpBackend_;
                $scope = _$rootScope_.$new();


                $q = _$q_;
            }));

            it('should handle a 200', function(done) {
                $httpBackend.when('GET', 'http://ads.domainblockercheck.eblocker.com/_check_/domain-blocker')
                .respond(function(method, url, data, headers){
                    return [200, 'data', {/* headers */}, 'No Content'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('passed');
                    expect(response.checkDetails.httpcode).toBe(200);
                    expect(response.checkDetails.httpdesc).toBe('No Content');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeAdsDomainBlockerTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a 404 (not found)', function(done) {
                $httpBackend.when('GET', 'http://ads.domainblockercheck.eblocker.com/_check_/domain-blocker')
                .respond(function(method, url, data, headers){
                    return [404, 'not found', {/* headers */}, 'Not Found'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(404);
                    expect(response.checkDetails.httpdesc).toBe('Not Found');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeAdsDomainBlockerTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a network error as such', function(done) {
                $httpBackend.when('GET', 'http://ads.domainblockercheck.eblocker.com/_check_/domain-blocker')
                .respond(function(method, url, data, headers){
                    return [-1, '', {/* headers */}, ''];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(-1);
                    expect(response.checkDetails.httpdesc).toBe('');
                    done();
                };
                spyObj.error = function() {
                    //will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                // the actual test call
                connectionTestService.makeAdsDomainBlockerTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });
        });

        describe('A domain blocker test (tracker-domains)', function() { // jshint ignore: line
            var $httpBackend;

            beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
                connectionTestService = _ConnectionTestService_;

                $httpBackend = _$httpBackend_;
                $scope = _$rootScope_.$new();


                $q = _$q_;
            }));

            it('should handle a 200', function(done) {
                $httpBackend.when('GET', 'http://tracker.domainblockercheck.eblocker.com/_check_/domain-blocker')
                .respond(function(method, url, data, headers){
                    return [200, 'data', {/* headers */}, 'No Content'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('passed');
                    expect(response.checkDetails.httpcode).toBe(200);
                    expect(response.checkDetails.httpdesc).toBe('No Content');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeTrackerDomainBlockerTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a 404 (not found)', function(done) {
                $httpBackend.when('GET', 'http://tracker.domainblockercheck.eblocker.com/_check_/domain-blocker')
                .respond(function(method, url, data, headers){
                    return [404, 'not found', {/* headers */}, 'Not Found'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(404);
                    expect(response.checkDetails.httpdesc).toBe('Not Found');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeTrackerDomainBlockerTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a network error as such', function(done) {
                $httpBackend.when('GET', 'http://tracker.domainblockercheck.eblocker.com/_check_/domain-blocker')
                .respond(function(method, url, data, headers){
                    return [-1, '', {/* headers */}, ''];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(-1);
                    expect(response.checkDetails.httpdesc).toBe('');
                    done();
                };
                spyObj.error = function() {
                    //will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                // the actual test call
                connectionTestService.makeTrackerDomainBlockerTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });
        });

        describe('A routing test', function() { // jshint ignore: line
            var $httpBackend;

            beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
                connectionTestService = _ConnectionTestService_;

                $httpBackend = _$httpBackend_;
                $scope = _$rootScope_.$new();


                $q = _$q_;
            }));

            it('should handle a 204 (no content)', function(done) {
                $httpBackend.when('GET', 'http://controlbar.eblocker.com/api/check/route')// TODO: insert actual domain
                .respond(function(method, url, data, headers){
                    return [204, 'data', {/* headers */}, 'No Content'];// TODO: verify content/http-code
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('passed');
                    expect(response.checkDetails.httpcode).toBe(204);
                    expect(response.checkDetails.httpdesc).toBe('No Content');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeRoutingTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a 404 (not found)', function(done) {
                $httpBackend.when('GET', 'http://controlbar.eblocker.com/api/check/route')
                .respond(function(method, url, data, headers){
                    return [404, 'not found', {/* headers */}, 'Not Found'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(404);
                    expect(response.checkDetails.httpdesc).toBe('Not Found');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeRoutingTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a network error as such', function(done) {
                $httpBackend.when('GET', 'http://controlbar.eblocker.com/api/check/route')
                .respond(function(method, url, data, headers){
                    return [-1, '', {/* headers */}, ''];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(-1);
                    expect(response.checkDetails.httpdesc).toBe('');
                    done();
                };
                spyObj.error = function() {
                    //will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                // the actual test call
                connectionTestService.makeRoutingTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });
        });
        
        describe('A dns firewall test', function() { // jshint ignore: line
            var $httpBackend;

            beforeEach(inject(function(_ConnectionTestService_, _$httpBackend_, _$q_, _$rootScope_) {
                connectionTestService = _ConnectionTestService_;

                $httpBackend = _$httpBackend_;
                $scope = _$rootScope_.$new();


                $q = _$q_;
            }));

            it('should handle a 204 (no content)', function(done) {
                $httpBackend.when('GET', 'http://dnscheck.eblocker.com/api/check/route')// TODO: insert actual domain
                .respond(function(method, url, data, headers){
                    return [204, 'data', {/* headers */}, 'No Content'];// TODO: verify content/http-code
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('passed');
                    expect(response.checkDetails.httpcode).toBe(204);
                    expect(response.checkDetails.httpdesc).toBe('No Content');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeDnsFirewallTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a 404 (not found)', function(done) {
                $httpBackend.when('GET', 'http://dnscheck.eblocker.com/api/check/route')
                .respond(function(method, url, data, headers){
                    return [404, 'not found', {/* headers */}, 'Not Found'];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(404);
                    expect(response.checkDetails.httpdesc).toBe('Not Found');
                    done();
                };
                spyObj.error = function() {
                    // will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                connectionTestService.makeDnsFirewallTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });

            it('should handle a network error as such', function(done) {
                $httpBackend.when('GET', 'http://dnscheck.eblocker.com/api/check/route')
                .respond(function(method, url, data, headers){
                    return [-1, '', {/* headers */}, ''];
                });
                const spyObj = {};
                spyObj.success = function(response) {
                    // actual test expectations
                    expect(response.checkStatus).toBe('failed');
                    expect(response.checkDetails.httpcode).toBe(-1);
                    expect(response.checkDetails.httpdesc).toBe('');
                    done();
                };
                spyObj.error = function() {
                    //will not be called
                    expect(true).toBe(false);
                };

                spyOn(spyObj, 'success').and.callThrough(); // jshint ignore: line
                spyOn(spyObj, 'error'); // jshint ignore: line

                // the actual test call
                connectionTestService.makeDnsFirewallTestRequest().then(spyObj.success, spyObj.error);

                $httpBackend.flush();

                // make sure only success has been called
                expect(spyObj.success).toHaveBeenCalled();
                expect(spyObj.error).not.toHaveBeenCalled();
            });
        });
    }
});
