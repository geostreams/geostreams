package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc.{Action, Controller}

/**
  * Datapoints contain the actual values together with a location and a time interval.
  */
@Singleton
class DatapointController @Inject() extends Controller {

  def addDatapoint(invalidateCache: Boolean) = Action {
    NotImplemented
  }

  def deleteDatapoint(id: String) = Action {
    NotImplemented
  }

  def searchDatapoints(operator: String, since: Option[String], until: Option[String], geocode: Option[String],
    stream_id: Option[String], sensor_id: Option[String], sources: List[String], attributes: List[String],
    format: String, semi: Option[String], onlyCount: Boolean) = Action {
    NotImplemented
  }

  def binDatapoints(time: String, depth: Double, keepRaw: Boolean, since: Option[String], until: Option[String],
    geocode: Option[String], stream_id: Option[String], sensor_id: Option[String], sources: List[String],
    attributes: List[String]) =  Action {
    NotImplemented
  }

  def getDatapoint(id: String) = Action {
    NotImplemented
  }
}
