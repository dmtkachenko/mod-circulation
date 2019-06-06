package org.folio.circulation.resources;

import static org.folio.circulation.domain.representations.RequestProperties.PROXY_USER_ID;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CreateRequestService;
import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.RequestRepository;
import org.folio.circulation.domain.RequestRepresentation;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateLoan;
import org.folio.circulation.domain.UpdateLoanActionHistory;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.RequestPolicyRepository;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonResponseResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RequestByInstanceIdResource extends Resource {

  private final Logger log;

  public RequestByInstanceIdResource(HttpClient client) {
    super(client);
    log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/requests/instances", router);

    routeRegistration.create(this::createInstanceLevelRequests);
  }

  private void createInstanceLevelRequests(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);

    final Result<RequestByInstanceIdRequest> requestByInstanceIdRequestResult =
      RequestByInstanceIdRequest.from(routingContext.getBodyAsJson());

    if(requestByInstanceIdRequestResult.failed()) {
      ResponseWritableResult<Object> failed = Result.failed(requestByInstanceIdRequestResult.cause());
      failed.writeTo(routingContext.response());
      return;
    }

    final RequestByInstanceIdRequest requestByInstanceIdRequest =
      requestByInstanceIdRequestResult.value();

    final ItemByInstanceIdFinder finder = new ItemByInstanceIdFinder(clients.holdingsStorage(), itemRepository);

    final InstanceRequestRelatedRecords requestRelatedRecords = new InstanceRequestRelatedRecords();
    requestRelatedRecords.setRequestByInstanceIdRequest(requestByInstanceIdRequest);

    finder.getItemsByInstanceId(requestByInstanceIdRequest.getInstanceId())
      .thenApply(r -> r.next(items -> getSeparatedItemsFromList(items, requestRelatedRecords)))
      .thenApply(r -> r.next(RequestByInstanceIdResource::rankItemsByMatchingServicePoint))
      .thenCompose(r -> r.after(relatedRecords -> combineWithUnavailableItems(relatedRecords, clients)))
      .thenApply( r -> r.next(RequestByInstanceIdResource::instanceToItemRequests))
      .thenCompose( r -> r.after( requests -> placeRequests(requests, clients)))
      .thenApply(r -> r.map(RequestAndRelatedRecords::getRequest))
      .thenApply(r -> r.map(new RequestRepresentation()::extendedRepresentation))
      .thenApply(CreatedJsonResponseResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequests(List<JsonObject> itemRequestRepresentations,
                                                                            Clients clients) {

    final RequestNoticeSender requestNoticeSender = RequestNoticeSender.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final CreateRequestService createRequestService = new CreateRequestService(
      RequestRepository.using(clients),
      new UpdateItem(clients),
      new UpdateLoanActionHistory(clients),
      new UpdateLoan(clients, loanRepository, new LoanPolicyRepository(clients)),
      new RequestPolicyRepository(clients),
      loanRepository, requestNoticeSender);

    return placeRequest(itemRequestRepresentations, 0, createRequestService, clients, loanRepository);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> placeRequest(List<JsonObject> itemRequests, int startIndex,
                                                                           CreateRequestService createRequestService, Clients clients,
                                                                           LoanRepository loanRepository) {
    final UserRepository userRepository = new UserRepository(clients);

    if (startIndex >= itemRequests.size()) {
      return CompletableFuture.completedFuture(failed(new ServerErrorFailure(
        "Failed to place a request for the title")));
    }

    JsonObject currentItemRequest = itemRequests.get(startIndex);

    final RequestFromRepresentationService requestFromRepresentationService =
      new RequestFromRepresentationService(
        new ItemRepository(clients, true, false, false),
        RequestQueueRepository.using(clients),
        userRepository,
        loanRepository,
        new ServicePointRepository(clients),
        createProxyRelationshipValidator(currentItemRequest, clients),
        new ServicePointPickupLocationValidator()
      );

    return requestFromRepresentationService.getRequestFrom(currentItemRequest)
      .thenCompose(r -> r.after(createRequestService::createRequest))
      .thenCompose(r -> {
          if (r.succeeded()) {
            return CompletableFuture.completedFuture(r);
          } else {
            log.debug("Failed to create request for {}", currentItemRequest.getString("id"));
            return placeRequest(itemRequests, startIndex +1, createRequestService, clients, loanRepository);
          }
        });
  }

  public static Result<InstanceRequestRelatedRecords> rankItemsByMatchingServicePoint(InstanceRequestRelatedRecords record) {

    final Collection<Item> unsortedAvailableItems = record.getUnsortedAvailableItems();
    final UUID pickupServicePointId = record.getRequestByInstanceIdRequest().getPickupServicePointId();

    return of(() -> {
      List<Item> itemsAtLocationServedByPickupPoint = unsortedAvailableItems.stream()
        .filter(item -> item.homeLocationIsServedBy(pickupServicePointId))
        .collect(Collectors.toList());

      List<Item> itemsNotAtLocationServedByPickupPoint = unsortedAvailableItems.stream()
        .filter(item -> !item.homeLocationIsServedBy(pickupServicePointId))
        .collect(Collectors.toList());

      final ArrayList<Item> rankedItems = new ArrayList<>();

      //Compose the final list of Items with the matchingItems (items that has matching service pointID) on top.
      rankedItems.addAll(itemsAtLocationServedByPickupPoint);
      rankedItems.addAll(itemsNotAtLocationServedByPickupPoint);

      record.setSortedAvailableItems(rankedItems);

      return record;
    });
  }

  public static Result<LinkedList<JsonObject>> instanceToItemRequests( InstanceRequestRelatedRecords requestRecords) {

    final RequestByInstanceIdRequest requestByInstanceIdRequest = requestRecords.getRequestByInstanceIdRequest();
    final List<Item> combineItems = requestRecords.getCombineItemsList();

    if (combineItems == null || combineItems.isEmpty()) {
      return failedValidation("Cannot create request objects when items list is null or empty", "items", "null");    }

    RequestType[] types = RequestType.values();
    LinkedList<JsonObject> requests = new LinkedList<>();

    final String defaultFulfilmentPreference = "Hold Shelf";

    for (Item item: combineItems) {
      for (RequestType reqType : types) {
        if (reqType != RequestType.NONE) {

          JsonObject requestBody = new JsonObject();
          requestBody.put("itemId", item.getItemId());
          requestBody.put("requestDate", requestByInstanceIdRequest.getRequestDate().toString(ISODateTimeFormat.dateTime()));
          requestBody.put("requesterId", requestByInstanceIdRequest.getRequesterId().toString());
          requestBody.put("pickupServicePointId", requestByInstanceIdRequest.getPickupServicePointId().toString());
          requestBody.put("fulfilmentPreference", defaultFulfilmentPreference);
          requestBody.put("requestExpirationDate",
            requestByInstanceIdRequest.getRequestExpirationDate().toString(ISODateTimeFormat.dateTime()));
          requestBody.put("requestType", reqType.name());

          requests.add(requestBody);
        }
      }
    }
    return succeeded(requests);
  }

  private Result<InstanceRequestRelatedRecords> getSeparatedItemsFromList(Collection<Item> items,
                                                                          InstanceRequestRelatedRecords requestRelatedRecords ){
    if (items == null ||items.isEmpty()) {
      return failedValidation("Items list is null or empty", "items", "null");
    }

    requestRelatedRecords.setUnsortedAvailableItems(
      items.stream()
        .filter(Item::isAvailable)
        .collect(Collectors.toList()));

    requestRelatedRecords.setUnsortedUnavailableItems(
      items.stream()
        .filter(item -> !item.isAvailable())
        .collect(Collectors.toList()));

    return succeeded(requestRelatedRecords);
  }

  private static CompletableFuture<Result<InstanceRequestRelatedRecords>> combineWithUnavailableItems(InstanceRequestRelatedRecords records, Clients clients){

    RequestQueueRepository queueRepository = RequestQueueRepository.using(clients);

    final Collection<Item> unsortedUnavailableItems = records.getUnsortedUnavailableItems();

    Map<Item, CompletableFuture<Result<RequestQueue>>> itemRequestQueueMap = new HashMap<>();
    if (unsortedUnavailableItems == null || unsortedUnavailableItems.isEmpty()) {
      return CompletableFuture.completedFuture(succeeded(records));
    }

    for (Item item : unsortedUnavailableItems) {
      itemRequestQueueMap.put(item, queueRepository.getLiteRequestQueues(item.getItemId()));
    }

    final Collection<CompletableFuture<Result<RequestQueue>>> requestQueueFutures = itemRequestQueueMap.values();

    //Collect the RequestQueue objects once they come back
    return CompletableFuture.allOf(requestQueueFutures.toArray(new CompletableFuture[requestQueueFutures.size()]))
      .thenApply(x -> {
        Map<Item, RequestQueue> itemQueueSizeMap = new HashMap<>();
        for (Map.Entry<Item, CompletableFuture<Result<RequestQueue>>> entry : itemRequestQueueMap.entrySet()) {
          Result<RequestQueue> requestQueueResult = entry.getValue().join();
          if (requestQueueResult.succeeded()) {
            itemQueueSizeMap.put(entry.getKey(), requestQueueResult.value());
          }
        }

        //Sort the map
        Map<Item, RequestQueue> sortedMap = sortRequestQueues(itemQueueSizeMap);
        Set<Item> sortedUnavailableItems = sortedMap.keySet();

        records.setSortedUnavailableItems(new ArrayList<>(sortedUnavailableItems));
        return succeeded(records);
      });
  }

  private static Map<Item, RequestQueue> sortRequestQueues(Map<Item,RequestQueue> unsortedItems){

    List<Map.Entry<Item, RequestQueue> > list = new LinkedList<>(unsortedItems.entrySet());

    // Sort the list
    Collections.sort(list, (q1, q2) -> {
      RequestQueue queue1 = q1.getValue();
      RequestQueue queue2 = q2.getValue();

      int result = queue1.size() - queue2.size();
      if (result == 0) {
        result = queue1.getLowestPriorityFulfillableRequest()
          .getRequestExpirationDate()
          .compareTo(queue2.getLowestPriorityFulfillableRequest().getRequestExpirationDate());
      }
      return result;
    });

    // put data from sorted list to hashmap
    HashMap<Item, RequestQueue> sortItems = new LinkedHashMap<>();
    for (Map.Entry<Item, RequestQueue> newEntry : list) {
      sortItems.put(newEntry.getKey(), newEntry.getValue());
    }

    return sortItems;
  }


  private ProxyRelationshipValidator createProxyRelationshipValidator(
    JsonObject representation,
    Clients clients) {

    return new ProxyRelationshipValidator(clients, () ->
      singleValidationError("proxyUserId is not valid",
        PROXY_USER_ID, representation.getString(PROXY_USER_ID)));
  }
}
