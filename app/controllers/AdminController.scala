package controllers

import javax.inject.{ Inject, Singleton }

import play.api.mvc.{ Action, Controller }

/**
 * Admin level endpoints.
 */
@Singleton
class AdminController @Inject() extends Controller {

  def deleteAll = Action {
    NotImplemented
  }

  def counts = Action {
    NotImplemented
  }

  def getConfig = Action {
    NotImplemented
  }
}
