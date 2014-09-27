package de.kp.spark.outlier
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

import com.typesafe.config.ConfigFactory
import org.apache.hadoop.conf.{Configuration => HConf}

object Configuration {

    /* Load configuration for router */
  val path = "application.conf"
  val config = ConfigFactory.load(path)

  def actor():Int = {
  
    val cfg = config.getConfig("actor")
    val timeout = cfg.getInt("timeout")
    
    timeout
    
  }

  def cache():Int = {
  
    val cfg = config.getConfig("cache")
    val maxentries = cfg.getInt("maxentries")
    
    maxentries
    
  }

  def elastic():HConf = {
  
    val cfg = config.getConfig("elastic")
    val conf = new HConf()                          

    conf.set("es.nodes",cfg.getString("es.nodes"))
    conf.set("es.port",cfg.getString("es.port"))

    conf.set("es.resource", cfg.getString("es.resource"))                
    conf.set("es.query", cfg.getString("es.query"))                          
 
    conf
    
  }
   
  def file():(String,String) = {
  
    val cfg = config.getConfig("file")
    
    val items = cfg.getString("items")   
    val features = cfg.getString("features")   
   
    (items,features)
    
  }

  def model():Map[String,String] = {
  
    val cfg = config.getConfig("model")
    
    Map(
      "amount.height" -> cfg.getString("amount.height"), 
      "amount.norm"   -> cfg.getString("amount.norm"), 

      "price.high" -> cfg.getString("price.high"), 

      "date.small"  -> cfg.getString("date.small"),
      "date.medium" -> cfg.getString("date.medium") 

    )
    
  }
  
  def redis():(String,String) = {
  
    val cfg = config.getConfig("redis")
    
    val host = cfg.getString("host")
    val port = cfg.getString("port")
    
    (host,port)
    
  }

  def router():(Int,Int,Int) = {
  
    val cfg = config.getConfig("router")
  
    val time    = cfg.getInt("time")
    val retries = cfg.getInt("retries")  
    val workers = cfg.getInt("workers")
    
    (time,retries,workers)

  }
  
  def spark():Map[String,String] = {
  
    val cfg = config.getConfig("spark")
    
    Map(
      "spark.executor.memory"          -> cfg.getString("spark.executor.memory"),
	  "spark.kryoserializer.buffer.mb" -> cfg.getString("spark.kryoserializer.buffer.mb")
    )

  }

}