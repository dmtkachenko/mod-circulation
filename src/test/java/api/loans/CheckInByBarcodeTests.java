package api.loans;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeTests extends APITests {
  @Test
  public void canCloseAnOpenLoanByCheckingInTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource loan = loansFixture.checkOut(nod, james, loanDate);

    DateTime expectedSystemReturnDate = DateTime.now(DateTimeZone.UTC);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(new DateTime(2018, 3, 5, 14 ,23, 41, DateTimeZone.UTC))
        .at(checkInServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    assertThat(loanRepresentation.getString("userId"), is(james.getId().toString()));

    assertThat("Should have return date",
      loanRepresentation.getString("returnDate"), is("2018-03-05T14:23:41.000Z"));

    assertThat("Should have system return date similar to now",
      loanRepresentation.getString("systemReturnDate"),
      is(withinSecondsAfter(Seconds.seconds(10), expectedSystemReturnDate)));

    assertThat("status is not closed",
      loanRepresentation.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      loanRepresentation.getString("action"), is("checkedin"));

    assertThat("ID should be included for item",
      loanRepresentation.getJsonObject("item").getString("id"), is(nod.getId()));

    assertThat("title is taken from item",
      loanRepresentation.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      loanRepresentation.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loanRepresentation.containsKey("itemStatus"), is(false));

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("title is included for item",
      itemFromResponse.getString("title"), is("Nod"));

    assertThat("ID should be included for item",
      itemFromResponse.getString("id"), is(nod.getId()));

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is("565578437802"));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not Available",
      storedLoan.getString("itemStatus"), is("Available"));

    assertThat("Checkin Service Point Id should be stored.",
      storedLoan.getString("checkinServicePointId"), is(checkInServicePointId));
  }

  @Test
  public void cannotCheckInItemThatCannotBeFoundByBarcode() {
    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .withItemBarcode("543593485458")
        .on(DateTime.now())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "No item with barcode 543593485458 exists")));
  }

  @Test
  public void cannotCheckInWithoutAServicePoint()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOut(nod, james, loanDate);

    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(DateTime.now())
        .atNoServicePoint());

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
        "Checkin request must have a service point id")));
  }

  @Test
  public void cannotCheckInWithoutAnItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOut(nod, james, loanDate);

    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .noItem()
        .on(DateTime.now())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an item barcode")));
  }

  @Test
  public void cannotCheckInWithoutACheckInDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOut(nod, james, loanDate);

    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .onNoOccasion()
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an check in date")));
  }

  @Test
  public void canCheckInAnItemWithoutAnOpenLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      nod, new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("ID should be included for item",
      itemFromResponse.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemFromResponse.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is("565578437802"));
  }

  @Test
  public void canCheckInAnItemTwice()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    loansFixture.checkOut(nod, james, loanDate);

    loansFixture.checkInByBarcode(nod,
      new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkInServicePointId);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      nod, new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("ID should be included for item",
      itemFromResponse.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemFromResponse.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is("565578437802"));
  }

  @Test
  public void patronNoticeOnCheckInIsSentWhenCheckInLoanNoticeIsDefinedAndLoanExists()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID checkInTemplateId = UUID.randomUUID();
    JsonObject checkOutNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(checkInTemplateId)
      .withCheckInEvent()
      .create();
    JsonObject renewNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType("Renew")
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with checkout notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfiguration, renewNoticeConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    loansFixture.checkOut(nod, james, loanDate);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(new DateTime(2018, 3, 5, 14 ,23, 41, DateTimeZone.UTC))
        .at(checkInServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    Awaitility.await()
      .atMost(5, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));

    List<JsonObject> sentNotices = patronNoticesClient.getAll();
    JsonObject notice = sentNotices.get(0);
    MatcherAssert.assertThat("sent notice should have template id form notice policy",
      notice.getString("templateId"), UUIDMatcher.is(checkInTemplateId));
    MatcherAssert.assertThat("sent notice should have email delivery channel",
      notice.getString("deliveryChannel"), CoreMatchers.is("email"));
    MatcherAssert.assertThat("sent notice should have output format",
      notice.getString("outputFormat"), CoreMatchers.is("text/html"));

    JsonObject noticeContext = notice.getJsonObject("context");
    MatcherAssert.assertThat("sent notice should have context property",
      noticeContext, notNullValue());
  }

  @Test
  public void shouldNotSendPatronNoticeWhenCheckInNoticeIsDefinedAndCheckInDoesNotCloseLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID checkInTemplateId = UUID.randomUUID();
    JsonObject checkOutNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(checkInTemplateId)
      .withCheckInEvent()
      .create();
    JsonObject renewNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType("Renew")
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with checkout notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfiguration, renewNoticeConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      nod, new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    TimeUnit.SECONDS.sleep(1);
    List<JsonObject> sentNotices = patronNoticesClient.getAll();
    assertThat("Check-in notice shouldn't be sent if item isn't checked-out",
      sentNotices, Matchers.empty());
  }
}
