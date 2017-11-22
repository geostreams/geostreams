package models

import utils.silhouette.IdentitySilhouette
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import javax.inject.Inject
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.functional.syntax._
import db.Users

case class User(
    id: Option[Int],
    email: String,
    emailconfirmed: Boolean,
    password: String,
    first_name: String,
    last_name: String,
    organization: String,
    /*
    * A user can register some accounts from third-party services, then it will have access to different parts of the
    * webpage. The 'master' privilege has full access.
    * Ex: ("master") -> full access to every point of the webpage.
    * Ex: ("serviceDownload") -> have access only to general and serviceDownload areas.
    */
    services: List[String]
) extends IdentitySilhouette {
  def key = email
  def fullName: String = first_name + " " + last_name
}

object User {
  implicit val userReads: Reads[User] = (
    (JsPath \ "id").readNullable[Int] and
    (JsPath \ "email").read[String] and
    (JsPath \ "emailconfirmed").read[Boolean] and
    (JsPath \ "password").read[String] and
    (JsPath \ "first_name").read[String] and
    (JsPath \ "last_name").read[String] and
    (JsPath \ "organization").read[String] and
    (JsPath \ "services").read[List[String]]
  )(User.apply _)

  implicit val userWrite = Json.writes[User]

  val services = Seq("serviceDownload", "master")

}
