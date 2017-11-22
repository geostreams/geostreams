package db

import com.google.inject.ImplementedBy
import db.postgres.PostgresUsers
import models.User
import play.api.libs.json.{ JsObject, JsValue }

/**
 * Access Datapoints store.
 */
@ImplementedBy(classOf[PostgresUsers])
trait Users {
  def get(id: Int): Option[User]
  def findByEmail(email: String): Option[User]
  def save(user: User): User
  def remove(email: String)
  def listAll(): List[User]
}
