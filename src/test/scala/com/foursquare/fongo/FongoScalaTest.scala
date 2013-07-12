package com.foursquare.fongo

import org.scalatest.Suite

import org.scalatest._

class FongoScalaTest extends Suite {

  def testFindOneNPE() {
    new Fongo("InMemoryMongo").getDB("db").getCollection("c").findOne() === null
  }
  
  def testFindNPE() {
    val fongo = new Fongo("InMemoryMongo")
    val db = fongo.getDB("myDB")
    val col = db.getCollection("c")
    val result = col.find()
    result.next() === null
  }
}