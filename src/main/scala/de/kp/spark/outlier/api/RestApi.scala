package de.kp.spark.outlier.api
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Spark-Outlier project
* (https://github.com/skrusche63/spark-outlier).
* 
* Spark-Outlier is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Spark-Outlier is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Spark-Outlier. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.Date

import org.apache.spark.SparkContext

import akka.actor.{ActorRef,ActorSystem,Props}
import akka.pattern.ask

import akka.util.Timeout

import spray.http.StatusCodes._

import spray.routing.{Directives,HttpService,RequestContext,Route}
import spray.routing.directives.CachingDirectives

import scala.concurrent.{ExecutionContext}
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt

import scala.util.parsing.json._

import de.kp.spark.core.model._
import de.kp.spark.core.rest.RestService

import de.kp.spark.outlier.actor.OutlierMaster
import de.kp.spark.outlier.Configuration

class RestApi(host:String,port:Int,system:ActorSystem,@transient val sc:SparkContext) extends HttpService with Directives {

  implicit val ec:ExecutionContext = system.dispatcher  
  import de.kp.spark.core.rest.RestJsonSupport._
  
  override def actorRefFactory:ActorSystem = system
 
  val (duration,retries,time) = Configuration.actor   
  val master = system.actorOf(Props(new OutlierMaster(sc)), name="outlier-master")
 
  private val service = "outlier"
    
  def start() {
    RestService.start(routes,system,host,port)
  }

  /*
   * The routes defines the different access channels this API supports
   */
  private def routes:Route = {

   /*
     * A 'fields' request supports the retrieval of the field
     * or metadata specificiations that are associated with
     * a certain training task (uid).
     * 
     * The approach actually supported enables the registration
     * of field specifications on a per uid basis, i.e. each
     * task may have its own fields. Requests that have to
     * refer to the same fields must provide the SAME uid
     */
    path("fields") {  
	  post {
	    respondWithStatus(OK) {
	      ctx => doFields(ctx)
	    }
	  }
    }  ~  
    /*
     * A 'register' request supports the registration of a field
     * or metadata specification that describes the fields used
     * to span the training dataset.
     */
    path("register" / Segment) {subject =>  
	  post {
	    respondWithStatus(OK) {
	      ctx => doRegister(ctx,subject)
	    }
	  }
    }  ~ 
    /*
     * 'index' and 'track' requests refer to the tracking functionality
     * of the Association Analysis engine; while 'index' prepares a
     * certain Elasticsearch index, 'track' is used to gather training
     * data.
     */
    path("index" / Segment) {subject =>  
	  post {
	    respondWithStatus(OK) {
	      ctx => doIndex(ctx,subject)
	    }
	  }
    }  ~ 
    path("track" / Segment) {subject => 
	  post {
	    respondWithStatus(OK) {
	      ctx => doTrack(ctx,subject)
	    }
	  }
    }  ~ 
    /*
     * A 'params' request supports the retrieval of the parameters
     * used for a certain model training task
     */
    path("params") {  
	  post {
	    respondWithStatus(OK) {
	      ctx => doParams(ctx)
	    }
	  }
    }  ~ 
    /*
     * A 'status' request supports the retrieval of the status
     * with respect to a certain training task (uid). The latest
     * status or all stati of a certain task are returned.
     */
    path("status" / Segment) {subject =>  
	  post {
	    respondWithStatus(OK) {
	      ctx => doStatus(ctx,subject)
	    }
	  }
    }  ~ 
    path("get" / Segment) {subject => 
	  post {
	    respondWithStatus(OK) {
	      ctx => doGet(ctx,subject)
	    }
	  }
    }  ~ 
    path("index" / Segment) {subject =>  
	  post {
	    respondWithStatus(OK) {
	      ctx => doIndex(ctx,subject)
	    }
	  }
    }  ~ 
    path("train") {
	  post {
	    respondWithStatus(OK) {
	      ctx => doTrain(ctx)
	    }
	  }
    } 
  
  }
  /**
   * 'fields' and 'register' requests refer to the metadata management; 
   * for a certain task (uid) and a specific model (name), a specification 
   * of the respective data fields can be registered and retrieved from a 
   * Redis database.
   * 
   * Request parameters for the 'fields' request:
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   */
  private def doFields[T](ctx:RequestContext) = doRequest(ctx,service,"fields")
  /**
   * Request parameters for the 'register' request:
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   * The information element, 'feature' or 'product' determines how to proceed:
   * 
   * topic: feature
   * 
   * - names (String, comma separated list of feature names)
   * - types (String, comma separated list of feature types)
   * 
   * topic:product
   * 
   * - user (String)
   * - timestamp (String) 
   * - group (String)
   * - item (Integer)
   * - price (Float)
   * 
   */    
  private def doRegister[T](ctx:RequestContext,subject:String) = {
 
    val task = "register:" + subject
    
    val topics = List("feature","product")
    if (topics.contains(subject)) doRequest(ctx,service,task)

  }
  
  /**
   * 'index' & 'track' requests support data registration in an Elasticsearch
   * index; while items are can be provided via the REST interface, rules are
   * built by the Outlier Detection engine and then registered in the index.
   * 
   * Request parameters for the 'index' request:
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   * - source (String)
   * - type (String)
   * 
   * The information element, 'feature' or 'product' determines how to proceed:
   * 
   * topic: feature
   * 
   * - names (String, comma separated list of feature names)
   * - types (String, comma separated list of feature types)
   * 
   * topic: product
   */
  private def doIndex[T](ctx:RequestContext,subject:String) = {

    val task = "index:" + subject
    
    val topics = List("feature","product")
    if (topics.contains(subject)) doRequest(ctx,service,task)
    
  }
  /**
   * Request parameters for the 'track' request:
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   * - source (String)
   * - type (String)
   * 
   * The information element, 'feature' or 'sequence' determines how to proceed:
   * 
   * topic: feature
   * 
   * - lbl. xxx (String, target value)
   * - fea. xxx (Double, predictor value) 
   * 
   * topic:product
   * 
   * - user (String)
   * - timestamp (Long)
   * - group (String)
   * - item (Integer)
   * - price (Float)
   * 
   */   
  private def doTrack[T](ctx:RequestContext,subject:String) = {

    val task = "track:" + subject
    
    val topics = List("feature","product")
    if (topics.contains(subject)) doRequest(ctx,service,task)
    
  }
  /**
   * 'status' is an administration request to determine whether a certain data
   * mining task has been finished or not.
   * 
   * Request parameters for the 'status' request:
   * 
   * - site (String)
   * - uid (String)
   * 
   */
  private def doStatus[T](ctx:RequestContext,subject:String) = {
    
    val task = "status:" + subject
    /*
     * The following topics are supported:
     * 
     * Retrieve the 'latest' status information about a certain
     * data mining or model building task.
     * 
     * Retrieve 'all' stati assigned to a certain data mining
     * or model building task.
     * 
     */
    val topics = List("latest","all")
    if (topics.contains(subject)) doRequest(ctx,service,task)
  
  }
  /**
   * Request parameters for the 'get' request:
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   */  
  private def doGet[T](ctx:RequestContext,subject:String) = {

    val task = "get:" + subject
    
    val topics = List("feature","product")
    if (topics.contains(subject)) doRequest(ctx,service,task)

  }
  /**
   * Request parameters for the 'train' request
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   * - algorithm (String, KMEANS, MARKOV)
   * - source (String, ELASTIC, FILE, JDBC, PARQUET)
   * 
   * and the following parameters depend on the selected source:
   * 
   * ELASTIC:
   * 
   * - source.index (String)
   * - source.type (String)
   * - query (String)
   * 
   * JDBC:
   * 
   * - query (String)
   * 
   * and the model building parameters have to be distinguished by the
   * selected algorithm
   * 
   * KMEANS:
   * 
   * - top (Integer)
   * - strategy (String, distance, entropy)
   * 
   * MARKOV:
   *  
   * - strategy (String, missprob, missrate, entreduc)
   * - threshold (Double)
   * 
   */
  private def doTrain[T](ctx:RequestContext) = doRequest(ctx,service,"train")
  /**
   * Request parameters for the 'params' request:
   * 
   * - site (String)
   * - uid (String)
   * - name (String)
   * 
   */
  private def doParams[T](ctx:RequestContext) = doRequest(ctx,service,"params")
  
  private def doRequest[T](ctx:RequestContext,service:String,task:String) = {
     
    val request = new ServiceRequest(service,task,getRequest(ctx))
    implicit val timeout:Timeout = DurationInt(time).second
    
    val response = ask(master,request).mapTo[ServiceResponse] 
    ctx.complete(response)
    
  }

  private def getHeaders(ctx:RequestContext):Map[String,String] = {
    
    val httpRequest = ctx.request
    
    /* HTTP header to Map[String,String] */
    val httpHeaders = httpRequest.headers
    
    Map() ++ httpHeaders.map(
      header => (header.name,header.value)
    )
    
  }
 
  private def getBodyAsMap(ctx:RequestContext):Map[String,String] = {
   
    val httpRequest = ctx.request
    val httpEntity  = httpRequest.entity    

    val body = JSON.parseFull(httpEntity.data.asString) match {
      case Some(map) => map
      case None => Map.empty[String,String]
    }
      
    body.asInstanceOf[Map[String,String]]
    
  }
  
  private def getRequest(ctx:RequestContext):Map[String,String] = {

    val headers = getHeaders(ctx)
    val body = getBodyAsMap(ctx)
    
    headers ++ body
    
  }

}