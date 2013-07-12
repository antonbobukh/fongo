package com.mongodb;

import com.foursquare.fongo.Fongo;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;

public class MockMongoClient extends MongoClient {
  private Fongo fongo;
  private WriteConcern concern;

  public MockMongoClient() throws UnknownHostException {

  }
  
  public void setFongo(Fongo fongo) {
    this.fongo = fongo;
  }

  @Override
  public WriteConcern getWriteConcern() {
    return concern;
  }

  @Override
  public void setWriteConcern(WriteConcern concern) {
    this.concern = concern;
  }

  @Override
  public String toString() {
    return fongo.toString();
  }

  @Override
  public Collection<DB> getUsedDatabases() {
    return fongo.getUsedDatabases();
  }

  @Override
  public List<String> getDatabaseNames() {
    return fongo.getDatabaseNames();
  }

  @Override
  public int getMaxBsonObjectSize() {
    return Bytes.MAX_OBJECT_SIZE;
  }

  @Override
  public DB getDB(String dbname) {
    return fongo.getDB(dbname);
  }

  @Override
  public void dropDatabase(String dbName) {
    fongo.dropDatabase(dbName);
  }
}