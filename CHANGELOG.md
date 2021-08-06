# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## Unreleased

### Changed
- Update ST_AsGeoJSON to make it work with PostGIS 3
  [PR#17](https://github.com/geostreams/geostreams/issues/17)

### Fixed
- When a sensor is deleted, any cached bins will also be deleted for that sensor [GEOD-1314](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1314)

## 3.2.0 - 2021-06-01 

### Added
- Support for nested parameters in cached trends endpoints [GEOD-1356](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1356)
### Changed


## 3.1.0 - 2020-12-14

### Added
- Swagger docs [GEOD-1319](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1319)
- Support for nested parameters in trends regions (for example when storing zooplankton for GLM) 
  [PR#4](https://github.com/geostreams/geostreams/pull/4)

### Changed
- Updated parameters.name, parameters.title, regions.title, and events.attributes column types.
  [GEOD-1361](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1361)

## [3.0.2] - 2020-08-27

### Fixed
- Fix permission issue on the docker entrypoint
  [GEOD-1348](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1343)

## [3.0.1] - 2020-08-27

### Fixed
- Improved speed of query for `GET /api/trends/region/:attribute`.
  [GEOD-1343](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1343)
 
## [3.0.0] - 2020-02-27
*Warning*: Requires Postgres schema update. See `geostreams.sql`.

### Fixed
- Improved datapoints downloading speed by adding a Postgres view.
 
## [3.0.0-beta.5] - 2019-05-16
### Added
- Event bus, record download actions
  [GEOD-1009](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1009)
- Authentication for downloads
  [GEOD-1003](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1003)
- Unit tests for sensors
  [GEOD-1017](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1017)
- Adding Banner
  [GEOD-1059](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1059)  
- Add sensor id to download URL. Adding support for download button in detail page
  [GEOD-1101](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1101)
- Save trends region to DB 
  [GEOD-1087](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1087)  
- Added parameter controller, model, sql creation script, postgres scripts for creating, updating, deleting and getting parameters.
  [GEOD-895](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-895)
- Add endpoint to delete a set of streams 
  [GEOD-1057](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1057)
- Storing bins in SQL tables for year, season, month, day, hour
  [GEOD_1082](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1082)
  [GEOD-1133](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1133)
- Implementing trends by station per season using a source. 
  [GEOD-1134](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1134)
- Added endpoint for analysis trends
  [GEOD-1062](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1062)
- Implemented counts endpoint for sensors, streams, datapoints and bins
  [GEOD-1154](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1154)
- Added logs for start and end time in binning endpoints
  [GEOD-1162](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1162)
  
### Changed
- Changed header and email title
- Changed most controllers from cookie authenticator to BearerToken authenticator. HomeController still uses Cookie authenticator
  [GEOD-1066](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1066)
- Replace count in SQL 
  [GEOD-1108](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1108)  
- Changed binning methods to be compatible with the front-end 
  [GEOD-1118](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1118)
- Move binning by season creation to a separate endpoint 
  [GEOD-1153](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1153)
- Changed Binning for stacked bar data, implemented only on season bins
  [GEOD-1155](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1155)
 
### Fixed 
- Out of memory error when user requests large downloads
  [GEOD-1015](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1015)
- Fixed bug for deleting parameters
  [GEOD-1177](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1177)

## [3.0.0-alpha.1] - 11-27-2017
### Added
- Sensor CRUD methods
  [GEOD-951](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-951)
- Add endpoint for modifying parameters
  [GEOD-880](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-880)
- Modifying configuration files to allow overriding
  [GEOD-971](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-971)
- Trends by regions, and details for regions endpoints
  [GEOD-943](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-943), 
  [GEOD-976](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-976),
  [GEOD-997](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-997)
- Signin page and authorization
  [GEOD-957](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-957),
  [GEOD-987](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-987)
- Add CORS filter
  [GEOD-990](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-990)
- Bulk add datapoints 

### Changed
- POST sensor just returns id of created sensor instead of full sensor object. 
  Query to get sensor is expensive since it calculates min / max time dynamically.
- Rename controller methods for consistency
  [GEOD-958](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-958)
- Remove four controllers and routes that were not yet implemented
  [GEOD-1309](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1309)
  
[3.0.0-alpha.1]: https://opensource.ncsa.illinois.edu/bitbucket/projects/GEOD/repos/geo-temporal-api-v2/browse
