# M1: Metro Parking Web Service Home Page

## Goal: 
To process data from the [OpenData car park API](https://opendata.transport.nsw.gov.au/data/dataset/car-park-api), to be served by the `metro-parking-app`

## Background:
* [OpenData car park API](https://opendata.transport.nsw.gov.au/data/dataset/car-park-api) currently provides data on 44 carparks (5 historical, 39 live)
  * Parking spots **do not include** motorcycle or disabled bays
    * Zones are not accurate at
      * Ashfield
      * Kogarah
      * Seven Hills
      * Manly Vale
      * Brookvale

* OpenData currently provides a catalog of [Datasets](https://opendata.transport.nsw.gov.au/data/dataset/) (240 - 24 apis, 23 GeoJson..)

All APIs
* **Quota Limit:** 60 000 requests/day = ~0.7 requests/second
* **Rate Limit:** 5 requests/second

Use **Authorization Header**: `Authorization: apikey <your-api-key>`

## Tasks:

### 1 Define APIs
* 


### 2 Create Controllers
*

### 3 Connect to MongDB Database
*

### 4 Search Bar
*

### 9 Security:
* CORs / Cookie Domain

## Out of Scope:

## Success Criteria:
- [ ]
- [ ] Home Page is reachable via public internet
