/*
 * Copyright 2012 Stephane Godbillon
 *
 * This sample is in the public domain.
 */
package controllers

import play.api._
import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.iteratee._

import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._

import reactivemongo.api._
import reactivemongo.bson.handlers.DefaultBSONHandlers.DefaultBSONReaderHandler

import scala.concurrent.{ExecutionContext, Future}

object Application extends Controller with MongoController {

  lazy val futureCollection :Future[Collection] = {
    val db = ReactiveMongoPlugin.db
    val collection = db.collection("acappedcollection")
    Future(collection)
  }

  def index = Action {
    Ok(views.html.index())
  }

  def watchCollection = WebSocket.using[JsValue] { request =>
    // WS'den gelen mesajı veritabanına yazar
    val in = Iteratee.flatten(
      futureCollection.map(
        collection => Iteratee.foreach[JsValue] { json =>
          println("received " + json)
          collection.insert(json)
        }
      )
    )

    // veritabanına girdi oluştuğunda 
    val out = {
      val futureEnumerator = futureCollection.map { collection =>
        // so we are sure that the collection exists and is a capped one
        val cursor = collection.find(Json.obj(),QueryOpts().tailable.awaitData)
        // ok, let's enumerate it
        cursor.enumerate
      }
      Enumerator.flatten(futureEnumerator)
    }

    // We're done!
    (in, out)
  }
}