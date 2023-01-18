package controllers

import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents

class CheckController(controllerComponents: ControllerComponents)
    extends AbstractController(controllerComponents) {
  def check: Action[AnyContent] = Action { implicit request =>
    Ok("running")
  }
}
