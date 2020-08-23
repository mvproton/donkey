package com.appsflyer.donkey.route;

import com.appsflyer.donkey.route.handler.RouterDefinition;
import com.appsflyer.donkey.route.ring.RingRouteCreatorSupplier;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.appsflyer.donkey.TestUtil.DEFAULT_PORT;
import static com.appsflyer.donkey.TestUtil.getDefaultAddress;
import static com.appsflyer.donkey.route.PathDescriptor.MatchType.REGEX;
import static io.vertx.core.http.HttpMethod.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@ExtendWith(VertxExtension.class)
public class IntegrationTest {
  
  private static final String dummyJson = "{\"foo\":\"bar\"}";
  
  private RouterFactory newRouterFactory(Vertx vertx, List<RouteDescriptor> routes) {
    return new RouterFactory(vertx, newHandlerConfig(routes));
  }
  
  private RouterDefinition newHandlerConfig(List<RouteDescriptor> routes) {
    return new RouterDefinition(routes);
  }
  
  private void assertContextSuccess(VertxTestContext testContext) throws
                                                                  Throwable {
    assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS));
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }
  
  private RequestOptions optionsForUri(String uri) {
    return new RequestOptions()
        .setHost(getDefaultAddress().host())
        .setPort(DEFAULT_PORT)
        .setURI(uri);
  }
  
  private Router defineRoutes(Vertx vertx, Checkpoint requestsServed) {
    Handler<RoutingContext> handler = ctx -> {
      ctx.response().end(ctx.request().params().toString());
      requestsServed.flag();
    };
    
    var getFoo = RouteDescriptor.create()
                                            .addMethod(GET)
                                            .path(PathDescriptor.create("/foo"))
                                            .addHandler(handler);
  
    var postFooBar = RouteDescriptor.create()
                                                .addMethod(POST)
                                                .path(PathDescriptor.create("/foo/bar"))
                                                .addHandler(handler);
  
    var postOrPutJson = RouteDescriptor.create()
                                                   .addMethod(POST)
                                                   .addMethod(PUT)
                                                   .path(PathDescriptor.create("/json"))
                                                   .addConsumes("application/json")
                                                   .addProduces("application/json")
                                                   .addHandler(handler);
  
    var getPathVariable = RouteDescriptor.create()
                                                     .addMethod(GET)
                                                     .path(PathDescriptor.create("/token/:tokenId"))
                                                     .addHandler(handler);
  
    var getRegexPath = RouteDescriptor.create()
                                                  .addMethod(GET)
                                                  .path(PathDescriptor.create("/id/(\\d+)", REGEX))
                                                  .addHandler(handler);
  
    var postComplexRegexPath = RouteDescriptor.create()
                                                          .addMethod(POST)
                                                          .path(PathDescriptor.create("/([a-z]+-company)/(\\d+)/(account.{3})-dept", REGEX))
                                                          .addHandler(handler);
    
    return newRouterFactory(
        vertx, List.of(getFoo,
                       postFooBar,
                       postOrPutJson,
                       getPathVariable,
                       getRegexPath,
                       postComplexRegexPath))
        .create(new RingRouteCreatorSupplier());
  }
  
  private Future<HttpServer> startServer(
      Vertx vertx, VertxTestContext testContext, Handler<HttpServerRequest> router) {
    Checkpoint serverStarted = testContext.checkpoint();
    Promise<HttpServer> promise = Promise.promise();
    vertx.createHttpServer()
         .requestHandler(router)
         .listen(DEFAULT_PORT, v -> {
           if (v.failed()) {
             testContext.failNow(v.cause());
           }
           serverStarted.flag();
           promise.complete(v.result());
         });
    return promise.future();
  }
  
  @Test
  void testRoutingByMethod(Vertx vertx, VertxTestContext testContext) throws
                                                                      Throwable {
    Checkpoint requestsServed = testContext.checkpoint(1);
    Checkpoint responsesReceived = testContext.checkpoint(2);
    
    Router router = defineRoutes(vertx, requestsServed);
    
    startServer(vertx, testContext, router)
        .onComplete(v -> {
          var client = WebClient.create(vertx);
          
          client.request(GET, getDefaultAddress(), "/foo?fizz=buzz")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.statusCode());
                  responsesReceived.flag();
                })));
          
          client.request(POST, getDefaultAddress(), "/foo")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(405, response.statusCode(),
                               "It should respond with Method Not Allowed");
                  responsesReceived.flag();
                })));
        });
    
    assertContextSuccess(testContext);
  }
  
  @Test
  void testRoutingByConsumeContentType(Vertx vertx, VertxTestContext testContext) throws
                                                                                  Throwable {
    Checkpoint requestsServed = testContext.checkpoint(1);
    Checkpoint responsesReceived = testContext.checkpoint(2);
    
    Router router = defineRoutes(vertx, requestsServed);
    
    startServer(vertx, testContext, router)
        .onComplete(v -> {
          
          var client = WebClient.create(vertx);
          
          client.request(POST, optionsForUri("/json"))
                .sendJson(dummyJson, testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.statusCode());
                  responsesReceived.flag();
                })));
          
          client.request(POST, getDefaultAddress(), "/json")
                .putHeader("content-type", "application/octet-stream")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(415, response.statusCode(),
                               "It should respond with Unsupported Media Type");
                  responsesReceived.flag();
                })));
        });
    
    assertContextSuccess(testContext);
  }
  
  @Test
  void testRoutingByProduceContentType(Vertx vertx, VertxTestContext testContext) throws
                                                                                  Throwable {
    Checkpoint requestsServed = testContext.checkpoint(1);
    Checkpoint responsesReceived = testContext.checkpoint(2);
    
    Router router = defineRoutes(vertx, requestsServed);
    
    startServer(vertx, testContext, router)
        .onComplete(v -> {
          var client = WebClient.create(vertx);
          
          client.request(PUT, optionsForUri("/json"))
                .putHeader("Accept", "application/json")
                .sendJson(dummyJson, testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.statusCode());
                  responsesReceived.flag();
                })));
          
          client.request(PUT, optionsForUri("/json"))
                .putHeader("Accept", "text/html")
                .sendJson(dummyJson, testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(406, response.statusCode(),
                               "It should respond with Not Acceptable");
                  responsesReceived.flag();
                })));
        });
    
    assertContextSuccess(testContext);
  }
  
  @Test
  void testRoutingWithPathVariable(Vertx vertx, VertxTestContext testContext) throws
                                                                              Throwable {
    Checkpoint requestsServed = testContext.checkpoint(1);
    Checkpoint responsesReceived = testContext.checkpoint(1);
    
    Router router = defineRoutes(vertx, requestsServed);
    
    startServer(vertx, testContext, router)
        .onComplete(v -> {
          var client = WebClient.create(vertx);
          
          client.request(GET, getDefaultAddress(), "/token/fizzbuzz?foo=bar&bazz=fuzz")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.statusCode());
                  assertEquals(
                      String.join(System.lineSeparator(), "foo: bar", "bazz: fuzz", "tokenId: fizzbuzz"),
                      response.bodyAsString().trim());
                  responsesReceived.flag();
                })));
        });
    
    assertContextSuccess(testContext);
  }
  
  @Test
  void testRoutingWithRegexPath(Vertx vertx, VertxTestContext testContext) throws
                                                                           Throwable {
    Checkpoint requestsServed = testContext.checkpoint(1);
    Checkpoint responsesReceived = testContext.checkpoint(2);
    
    Router router = defineRoutes(vertx, requestsServed);
    
    startServer(vertx, testContext, router)
        .onComplete(v -> {
          var client = WebClient.create(vertx);
          
          client.request(GET, getDefaultAddress(), "/id/12345")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.statusCode());
                  assertEquals("param0: 12345", response.bodyAsString().trim());
                  responsesReceived.flag();
                })));
          
          client.request(GET, getDefaultAddress(), "/id/not-a-number")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(404, response.statusCode(),
                               "It should respond with Not Found");
                  responsesReceived.flag();
                })));
        });
    
    assertContextSuccess(testContext);
  }
  
  @Test
  void testRoutingWithComplexRegexPath(Vertx vertx, VertxTestContext testContext) throws
                                                                                  Throwable {
    Checkpoint requestsServed = testContext.checkpoint(1);
    Checkpoint responsesReceived = testContext.checkpoint(1);
    
    Router router = defineRoutes(vertx, requestsServed);
    
    startServer(vertx, testContext, router)
        .onComplete(v -> {
          var client = WebClient.create(vertx);
          
          client.request(POST, getDefaultAddress(), "/xyz-company/321/accounting-dept")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.statusCode());
                  assertEquals(
                      String.join(System.lineSeparator(),
                                  "param0: xyz-company",
                                  "param1: 321",
                                  "param2: accounting"),
                      response.bodyAsString().trim());
                  responsesReceived.flag();
                })));
          
          client.request(POST, getDefaultAddress(), "/xyz-company/321/marketing-dept")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(404, response.statusCode(), "It should respond with Not Found");
                  responsesReceived.flag();
                })));
          
        });
    
    assertContextSuccess(testContext);
  }
  
}
