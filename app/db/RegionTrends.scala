package db

import com.google.inject.ImplementedBy
import db.postgres.{ PostgresDatapoints, PostgresRegionTrends }
import models.{ DatapointModel, RegionModel }
import play.api.libs.json.{ JsObject, JsValue }

@ImplementedBy(classOf[PostgresRegionTrends])
trait RegionTrends {
  def trendsByRegion(attribute: String, geocode: String, latFirst: Boolean): List[JsValue]
  def getTrendsByRegion(parameter: String, season: String): List[JsObject]
  // save region once, no update function
  def saveRegion(region: RegionModel): Unit
  def saveRegionTrends(region: RegionModel, season: String, parameter: String, average: List[Double]): Unit
}
