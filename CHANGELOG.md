# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

### Changed
- POST sensor just returns id of created sensor instead of full sensor object. 
  Query to get sensor is expensive since it calculates min / max time dynamically.

[Unreleased]: https://opensource.ncsa.illinois.edu/bitbucket/users/lmarini/repos/geostreams