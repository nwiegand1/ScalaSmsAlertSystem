package controllers

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.{PublishRequest, PublishResult}
import javax.inject.{Inject, Singleton}
import models.{FollowUp, FreeFormMsgs, SubscriberRepository}
import controllers.FreeFormController._
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{AbstractController, ControllerComponents, Result}

import scala.concurrent.{ExecutionContext, Future}
import models.Subscriber.SubscriberLangMap
import play.api.Logger
import play.api.data.Form

@Singleton
class FreeFormController @Inject()(cc: ControllerComponents, repo: SubscriberRepository, messagesApi: MessagesApi, amazonSns: AmazonSNS)
                                  (implicit ec: ExecutionContext)
  extends AbstractController(cc) with I18nSupport with AuthenticatedActionSupport {

  def get = authenticatedAction { implicit request =>
    Ok(views.html.freeform(FreeForm, this))
  }

  private def sendFollowSms(followup: FollowUp): Future[Result] = {
    repo
      .listActive()
      .map { subscribers =>
        subscribers.foreach { subscriber =>
          implicit val lang: Lang = Lang.get(SubscriberLangMap(subscriber.language.get)).get

          // TODO: remove this because it does not apply to every language
          val personWord = if (followup.numberPeople == 1) "person" else "people"
          val smsBody: String = messagesApi(
            "follow_up", followup.numberPeople, personWord, followup.city, followup.targetName, followup.targetPhoneNumber
          )

          val result: PublishResult = amazonSns.publish(
            new PublishRequest()
              .withPhoneNumber(subscriber.phone)
              .withMessage(smsBody)
          )

          Logger.info(s"Sent text message to ${subscriber.phone} with message id ${result.getMessageId}")
        }

        Redirect(routes.HomeController.index())
          .flashing("success" -> s"Done! Sent ${subscribers.size} messages")
      }
  }
}

private object FreeFormController {

  private val FreeForm = {
    Form(
    mapping(
      "eng" -> nonEmptyText,
      "spa" -> nonEmptyText
    )(models.FreeFormMsgs.apply)(models.FreeFormMsgs.unapply)
    )
  }
}
