package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresEvents
import models.User
import com.mohiva.play.silhouette.api.Identity
import play.api.libs.json.JsValue

import scala.util.parsing.json.JSONObject

/**
 * Access Events store.
 */
@ImplementedBy(classOf[PostgresEvents])
trait Events {
  def save(id: Option[Int], request: Map[String, Seq[String]]): Unit
  def listAll(): List[JsValue]
  def getLatestPurpose(userId: Int): String
}
