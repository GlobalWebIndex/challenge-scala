package controllers

import play.api.mvc.{AbstractController, Action, ControllerComponents}

class CheckController(controllerComponents: ControllerComponents)
    extends AbstractController(controllerComponents) {
  def check: Action[Unit] = Action(parse.empty) { implicit request =>
    Ok("running")
  }
}
