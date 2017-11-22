package controllers

import javax.inject.{ Inject, Singleton }

import play.api.mvc.{ Action, Controller }

/**
 * Caches are kept to retrieve binned versions of the data (datatapoints) and trends.
 */
@Singleton
class CacheController @Inject() extends Controller {

  def cacheInvalidateAction(sensor_id: Option[String] = None, stream_id: Option[String] = None) = Action {
    NotImplemented
  }

  def cacheListAction() = Action {
    NotImplemented
  }

  def cacheFetchAction(filename: String) = Action {
    NotImplemented
  }
}
