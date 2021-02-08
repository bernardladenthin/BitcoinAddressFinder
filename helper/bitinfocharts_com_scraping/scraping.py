#
# Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
from selenium import webdriver
import time
from io import BytesIO
import re

# pip install selenium

def findAllInSource(addressPattern, driver):
  html = driver.page_source
  return re.findall(addressPattern, html)

def getAllUrls(urlPattern):
  urls = []
  for x in range(1, 100+1):
    pageIndex = "-"+str(x)
    if x == 1:
        pageIndex = ""
    fullUrl = urlPattern.format(pageIndex)
    urls.append(fullUrl)
  return urls

def scrapeAddresses(urlPattern, addressPattern, driver):
    urls = getAllUrls(urlPattern)
    addresses = set()
    for url in urls:
        driver.get(url)
        time.sleep(2)
        allInSource = findAllInSource(addressPattern, driver)
        addresses.update(allInSource)
    return addresses

def writeToFile(addressSet, filename):
    outF = open(filename, "w")
    for address in addressSet:
        outF.write(address)
        outF.write("\n")
    outF.close()

driver = webdriver.Chrome('C:\\Users\\Bernard\\Desktop\\chromedriver87.exe')
# manual input for captcha
#driver.get("https://bitinfocharts.com/de/top-100-richest-bitcoin-addresses.html")
#time.sleep(30)

base58Pattern = "[a-km-zA-HJ-NP-Z1-9]{25,34}"

bitcoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-bitcoin-addresses{}.html"
bitcoinAddressPattern = "[13]"+base58Pattern
bitcoins = scrapeAddresses(bitcoinUrlPattern, bitcoinAddressPattern, driver)
writeToFile(bitcoins, "bitcoin.txt")

litecoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-litecoin-addresses{}.html"
litecoinAddressPattern = "[LM]"+base58Pattern
litecoins = scrapeAddresses(litecoinUrlPattern, litecoinAddressPattern, driver)
writeToFile(litecoins, "litecoin.txt")

bitcoincashUrlPattern = "https://bitinfocharts.com/de/top-100-richest-bitcoin%20cash-addresses{}.html"
bitcoincashAddressPattern = "[13]"+base58Pattern
bitcoincashs = scrapeAddresses(bitcoincashUrlPattern, bitcoincashAddressPattern, driver)
writeToFile(bitcoincashs, "bitcoincash.txt")

bitcoinsvUrlPattern = "https://bitinfocharts.com/de/top-100-richest-bitcoin%20sv-addresses{}.html"
bitcoinsvAddressPattern = "[13]"+base58Pattern
bitcoinsvs = scrapeAddresses(bitcoinsvUrlPattern, bitcoinsvAddressPattern, driver)
writeToFile(bitcoinsvs, "bitcoinsv.txt")

dashUrlPattern = "https://bitinfocharts.com/de/top-100-richest-dash-addresses{}.html"
dashAddressPattern = "[X]"+base58Pattern
dashs = scrapeAddresses(dashUrlPattern, dashAddressPattern, driver)
writeToFile(dashs, "dash.txt")

dogecoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-dogecoin-addresses{}.html"
dogecoinAddressPattern = "[D]"+base58Pattern
dogecoins = scrapeAddresses(dogecoinUrlPattern, dogecoinAddressPattern, driver)
writeToFile(dogecoins, "dogecoin.txt")

bitcoingoldUrlPattern = "https://bitinfocharts.com/de/top-100-richest-bitcoin%20gold-addresses{}.html"
bitcoingoldAddressPattern = "[G]"+base58Pattern
bitcoingolds = scrapeAddresses(bitcoingoldUrlPattern, bitcoingoldAddressPattern, driver)
writeToFile(bitcoingolds, "bitcoingold.txt")

reddcoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-reddcoin-addresses{}.html"
reddcoinAddressPattern = "[R]"+base58Pattern
reddcoins = scrapeAddresses(reddcoinUrlPattern, reddcoinAddressPattern, driver)
writeToFile(reddcoins, "reddcoin.txt")

namecoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-namecoin-addresses{}.html"
namecoinAddressPattern = "[N]"+base58Pattern
namecoins = scrapeAddresses(namecoinUrlPattern, namecoinAddressPattern, driver)
writeToFile(namecoins, "namecoin.txt")

vertcoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-vertcoin-addresses{}.html"
vertcoinAddressPattern = "[V]"+base58Pattern
vertcoins = scrapeAddresses(vertcoinUrlPattern, vertcoinAddressPattern, driver)
writeToFile(vertcoins, "vertcoin.txt")

novacoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-novacoin-addresses{}.html"
novacoinAddressPattern = "[4]"+base58Pattern
novacoins = scrapeAddresses(novacoinUrlPattern, novacoinAddressPattern, driver)
writeToFile(novacoins, "novacoin.txt")

blackcoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-blackcoin-addresses{}.html"
blackcoinAddressPattern = "[B]"+base58Pattern
blackcoins = scrapeAddresses(blackcoinUrlPattern, blackcoinAddressPattern, driver)
writeToFile(blackcoins, "blackcoin.txt")

feathercoinUrlPattern = "https://bitinfocharts.com/de/top-100-richest-feathercoin-addresses{}.html"
feathercoinAddressPattern = "[7]"+base58Pattern
feathercoins = scrapeAddresses(feathercoinUrlPattern, feathercoinAddressPattern, driver)
writeToFile(feathercoins, "feathercoin.txt")
# time.sleep(120)

if (False): '''
'''
driver.close()
