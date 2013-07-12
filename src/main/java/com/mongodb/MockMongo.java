package com.mongodb;

import java.lang.reflect.Method;

import com.foursquare.fongo.Fongo;
import java.util.Collection;
import java.util.List;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
 

public class MockMongo {
  
  public static MongoClient create(final Fongo fongo) {
    System.out.println("Creating a new MongoClient");
    try {
      MockMongoClient mockMongoClient = (MockMongoClient)Enhancer.create(MockMongoClient.class, new MockMongoClientInterceptor());
      mockMongoClient.setFongo(fongo);
      return mockMongoClient;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public static class MockMongoClientInterceptor implements MethodInterceptor {
    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
      if ("isMongosConnection".equals(method.getName())) {
        System.out.println("Intercepted isMongosConnetction, returning false");
        return false;
      }
      return proxy.invokeSuper(obj, args);
    }
    
  }
  
  public static Mongo createMock(final Fongo fongo) {
    try {
      Mongo.class.getDeclaredMethod("isMongosConnection").setAccessible(true);
    } catch (Exception e) {
      throw new RuntimeException("Couldn't mark isMongosConnection as accessible", e);
    }
    Mongo mongo = Mockito.mock(Mongo.class);
    Mockito.when(mongo.toString()).thenReturn(fongo.toString());
    Mockito.when(mongo.getMongoOptions()).thenReturn(new MongoOptions());
    Mockito.when(mongo.getDB(Mockito.anyString())).thenAnswer(new Answer<DB>(){
      @Override
      public DB answer(InvocationOnMock invocation) throws Throwable {
        String dbName = (String) invocation.getArguments()[0];
        return fongo.getDB(dbName);
      }});
    Mockito.when(mongo.getUsedDatabases()).thenAnswer(new Answer<Collection<DB>>(){
      @Override
      public Collection<DB> answer(InvocationOnMock invocation) throws Throwable {
        return fongo.getUsedDatabases();
      }});
    Mockito.when(mongo.getDatabaseNames()).thenAnswer(new Answer<List<String>>(){
      @Override
      public List<String> answer(InvocationOnMock invocation) throws Throwable {
        return fongo.getDatabaseNames();
      }});
    Mockito.doAnswer(new Answer<Void>(){
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        String dbName = (String) invocation.getArguments()[0];
        fongo.dropDatabase(dbName);
        return null;
      }}).when(mongo).dropDatabase(Mockito.anyString());
    Mockito.when(mongo.isMongosConnection()).thenReturn(false);

    return mongo;
  }
}
