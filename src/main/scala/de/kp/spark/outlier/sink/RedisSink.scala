package de.kp.spark.outlier.sink
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

import de.kp.spark.core.Names

import de.kp.spark.outlier.model._
import de.kp.spark.outlier.spec.Features

import de.kp.spark.core.model._
import de.kp.spark.core.redis.RedisClient

import de.kp.spark.outlier.Configuration

import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer

class RedisSink {

  val (host,port) = Configuration.redis
  val client = RedisClient(host,port.toInt)
  
  val service = "outlier"

  def addFOutliers(req:ServiceRequest, outliers:FOutliers) {
   
    val now = new Date()
    val timestamp = now.getTime()
    
    val k = "feature:" + req.data(Names.REQ_SITE) + ":" + req.data(Names.REQ_UID) + ":" + req.data(Names.REQ_NAME) 
    val v = "" + timestamp + ":" + Serializer.serializeFOutliers(outliers)
    
    client.zadd(k,timestamp,v)
    
  }

  def addBOutliers(req:ServiceRequest, outliers:BOutliers) {
   
    val now = new Date()
    val timestamp = now.getTime()
    
    val k = "behavior:" + req.data(Names.REQ_SITE) + ":" + req.data(Names.REQ_UID) + ":" + req.data(Names.REQ_NAME) 
    val v = "" + timestamp + ":" + Serializer.serializeBOutliers(outliers)
    
    client.zadd(k,timestamp,v)
    
  }
  
  def behaviorExists(req:ServiceRequest):Boolean = {

    val k = "behavior:" + req.data(Names.REQ_SITE) + ":" + req.data(Names.REQ_UID) + ":" + req.data(Names.REQ_NAME) 
    client.exists(k)
    
  }
 
  def featuresExist(req:ServiceRequest):Boolean = {

    val k = "feature:" + req.data(Names.REQ_SITE) + ":" + req.data(Names.REQ_UID) + ":" + req.data(Names.REQ_NAME) 
    client.exists(k)
    
  }
  
  def features(req:ServiceRequest):String = {

    val spec = Features.get(req)

    val k = "feature:" + req.data(Names.REQ_SITE) + ":" + req.data(Names.REQ_UID) + ":" + req.data(Names.REQ_NAME) 
    val features = client.zrange(k, 0, -1)

    if (features.size() == 0) {
      Serializer.serializeFOutliers(FOutliers(List.empty[(Double,LabeledPoint)]))
    
    } else {
      
      val last = features.toList.last
      last.split(":")(1)
      
    }
  }
  
  def behavior(req:ServiceRequest):String = {

    val k = "behavior:" + req.data(Names.REQ_SITE) + ":" + req.data(Names.REQ_UID) + ":" + req.data(Names.REQ_NAME) 
    val behavior = client.zrange(k, 0, -1)

    if (behavior.size() == 0) {
      Serializer.serializeBDetections(new BDetections(List.empty[BDetection]))
    
    } else {
      
      val last = behavior.toList.last
      val outliers = Serializer.deserializeBOutliers(last.split(":")(1)).items

      val detections = outliers.map(o => {
        new BDetection(o._1,o._2,o._3,o._4,o._5)
      }).toList
       
      Serializer.serializeBDetections(new BDetections(detections))

    }
  
  }

}