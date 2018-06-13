import javax.inject.Inject

import play.api.http.DefaultHttpFilters

import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import play.filters.hosts.AllowedHostsFilter
import play.filters.cors.CORSFilter
import play.filters.gzip.GzipFilter

/**
 * Add the following filters by default to all projects
 *
 * https://www.playframework.com/documentation/latest/ScalaCsrf
 * https://www.playframework.com/documentation/latest/AllowedHostsFilter
 * https://www.playframework.com/documentation/latest/SecurityHeaders
 * https://www.playframework.com/documentation/2.6.x/GzipEncoding#Configuring-gzip-encoding
 */
class Filters @Inject() (
  csrfFilter: CSRFFilter,
  allowedHostsFilter: AllowedHostsFilter,
  //  securityHeadersFilter: SecurityHeadersFilter,
  corsFilter: CORSFilter,
  gzipFilter: GzipFilter
) extends DefaultHttpFilters(
  csrfFilter,
  allowedHostsFilter,
  // disable securityHeadersFilter for current, maybe move back
  // securityHeadersFilter,
  corsFilter,
  // gzip seems working without this filter
  gzipFilter
)