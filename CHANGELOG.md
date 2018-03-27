# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).
 
## [Unreleased]
### Added
- Event bus, record download actions
  [GEOD-1009](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1009)
- Authentication for downloads
  [GEOD-1003](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1003)
- Unit tests for sensors
  [GEOD-1017](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1017)
- Adding Banner
  [GEOD-1059](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1059)  
 
### Changed
- Changed header and email title

### Fixed 
- Out of memory error when user requests large downloads
  [GEOD-1015](https://opensource.ncsa.illinois.edu/jira/browse/GEOD-1015)

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

[3.0.0-alpha.1]: https://opensource.ncsa.illinois.edu/bitbucket/projects/GEOD/repos/geo-temporal-api-v2/browse