package utils.silhouette

import db.Users
import models.User
import Implicits._
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import scala.concurrent.Future
import javax.inject.Inject

// The function is duplicate with UserDB.
class UserService @Inject() (users: Users) extends IdentityService[User] {
  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = Future.successful(users.findByEmail(loginInfo))
  def save(user: User): Future[User] = Future.successful(users.save(user))
}