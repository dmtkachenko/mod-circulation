package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

/**
 * Loans anonymization scheduler.
 */
public class ScheduledAnonymizationResource extends Resource {

  public ScheduledAnonymizationResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/scheduled-anonymize-processing", router);

    routeRegistration.create(this::initPeriodicForTenant);
  }

  private void initPeriodicForTenant(RoutingContext routingContext) {
    final Clients clients = Clients.create(new WebContext(routingContext), client);
    anonymizeLoansViaIntervalConfiguration(clients);

    routingContext.response().setStatusCode(204).end();
  }

  private void anonymizeLoansViaIntervalConfiguration(Clients clients) {
    ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);

    //TODO: add here call to the AnonymizeService.
    configurationRepository.lookupConfigurationPeriod();
  }
}
