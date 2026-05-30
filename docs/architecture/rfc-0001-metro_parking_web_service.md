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

See [API Basics](https://opendata.transport.nsw.gov.au/developers/api-basics)
## Tasks:

### 1 Priming Development
1. Linting: Spotless
2. Database: MongoDB
3. OpenAPI/Swagger: Springdoc
4. Actuator: Spring Boot

### 2 Definitions: Server (OpenData) and Client
1. Server DTOs (request and response)
2. Client Controller/Service

### 3: 

### 4 Connect to MongDB Database
*

### 5 Search Bar
*

### 9 Security:
* CORs / Cookie Domain

## Out of Scope:

## Success Criteria:
- [ ]
- [ ] Home Page is reachable via public internet
