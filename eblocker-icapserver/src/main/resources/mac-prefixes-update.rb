#!/usr/bin/env ruby
#
# Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
#
# Licensed under the EUPL, Version 1.2 or - as soon they will be
# approved by the European Commission - subsequent versions of the EUPL
# (the "License"); You may not use this work except in compliance with
# the License. You may obtain a copy of the License at:
#
#   https://joinup.ec.europa.eu/page/eupl-text-11-12
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing
# permissions and limitations under the License.
#

require 'net/http'

puts "Downloading oui.txt..."
uri = URI('http://standards-oui.ieee.org/oui.txt')
oui = Net::HTTP.get(uri)

puts "Writing mac-prefixes.txt..."
File.open('mac-prefixes.txt', 'w') do |file|
  oui.each_line do |line|
    if line =~ /\s*([0-9A-F]{2})-([0-9A-F]{2})-([0-9A-F]{2})\s+\(hex\)\s+(.*)/
      file.puts(["#$1#$2#$3".downcase, $4.rstrip].join(':'))
    end
  end
end

puts "Finished. You might want to sort mac-prefixes.txt before committing."
