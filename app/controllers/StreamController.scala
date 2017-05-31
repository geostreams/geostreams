package controllers

import javax.inject.{Inject, Singleton}

import play.api.mvc.{Action, Controller}

/**
  * Streams are a way of grouping datapoints together. A stream has to belong to a `Sensor` and and `Datapoint` has to
  * belong to a `Stream`.
  */
@Singleton
class StreamController @Inject() extends Controller {
  def createStream() = Action {
    NotImplemented
  }

  def updateStatisticsStreamSensor() = Action {
    NotImplemented
  }

  def getStream(id: String) = Action {
    NotImplemented
  }

  def patchStreamMetadata(id: String) = Action {
    NotImplemented
  }

  def updateStatisticsStream(id: String) = Action {
    NotImplemented
  }

  def searchStreams(geocode: Option[String], stream_name: Option[String]) = Action {
    NotImplemented
  }

  def deleteStream(id: String) = Action {
    NotImplemented
  }
}
