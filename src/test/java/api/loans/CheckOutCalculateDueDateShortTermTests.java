package api.loans;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.domain.policy.LoansPolicyProfile;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.fixtures.CalendarExamples.*;
import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMATTER;
import static org.folio.circulation.support.PeriodUtil.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test case for Short-term loans
 * Loanable = Y
 * Loan profile = Rolling
 * Closed Library Due Date Management = Move to the end of the current service point hours
 * <p>
 * Expected result:
 * If SPID-1 is determined to be CLOSED for system-calculated due date and timestamp
 * Then the due date timestamp should be changed to the endTime of the current service point for SPID-1 (i.e., truncating the loan length)
 */
public class CheckOutCalculateDueDateShortTermTests extends APITests {

  private static final String LOAN_POLICY_NAME = "Move to the beginning of the next open service point hours";
  private static final String POLICY_PROFILE_NAME = LoansPolicyProfile.ROLLING.name();
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";

  /**
   * Loan period: Hours
   * Current day: closed
   * Next day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testHoursLoanPeriodIfCurrentDayIsClosedAndNextAllDayOpen() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 24);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testHoursLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 24);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Minutes
   * Current day: closed
   * Next day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testMinutesLoanPeriodIfCurrentDayIsClosedAndNextAllDayOpen() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 59);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration);
  }

  /**
   * Loan period: Minutes
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testMinutesLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 24);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration);
  }

  /**
   * Loan period: Hours
   * Current day: open allDay
   * Current and next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testHoursLoanPeriodIfAllDayOpen() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 24);
    String interval = INTERVAL_HOURS;

    OpeningDayPeriod currentDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getTimeOpeningDay(currentDay.getOpeningDay(), interval, duration);

    checkOffsetTime(expectedDueDate, servicePointId, interval, duration);
  }

  /**
   * Loan period: Hours
   * Current day: open allDay
   * Current and next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testHoursLoanPeriodIfDayHasPeriod() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 24);
    String interval = INTERVAL_HOURS;

    List<OpeningDayPeriod> openingDayPeriods = getFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getTimeOpeningPeriodDay(openingDayPeriods, interval, duration);

    checkOffsetTime(expectedDueDate, servicePointId, interval, duration);
  }

  /**
   * Loan period: Minutes
   * Current day: open allDay
   * Current and next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMinutesLoanPeriodIfAllDayOpen() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 59);
    String interval = INTERVAL_MINUTES;

    OpeningDayPeriod currentDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getTimeOpeningDay(currentDay.getOpeningDay(), interval, duration);

    checkOffsetTime(expectedDueDate, servicePointId, interval, duration);
  }

  /**
   * Loan period: Minutes
   * Current day: open allDay
   * Current and next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMinutesLoanPeriodIfDayHasPeriod() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(0, 59);
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDayPeriods = getFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getTimeOpeningPeriodDay(openingDayPeriods, interval, duration);

    checkOffsetTime(expectedDueDate, servicePointId, interval, duration);
  }

  /**
   * Check result
   */
  private void checkOffsetTime(DateTime expectedDueDate, String servicePointId,
                               String interval, int duration)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyOffsetTimeEntry(duration, interval);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime actualDueDate = getThresholdDateTime(DateTime.parse(loan.getString("dueDate")));
    DateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);

    assertThat("due date should be " + thresholdDateTime + ", actual due date is " + actualDueDate,
      actualDueDate.compareTo(thresholdDateTime) == 0);
  }

  private DateTime getTimeOpeningDay(OpeningDay openingDay, String interval, int duration) {
    LoanPolicyPeriod period = interval.equals(INTERVAL_HOURS)
      ? LoanPolicyPeriod.HOURS
      : LoanPolicyPeriod.MINUTES;
    LocalDate dateOfDay = LocalDate.parse(openingDay.getDate(), DATE_TIME_FORMATTER);
    LocalTime timeShift = getTimeShift(LocalTime.now(ZoneOffset.UTC), period, duration);
    return getDateTimeZoneRetain(LocalDateTime.of(dateOfDay, timeShift));
  }

  private DateTime getEndDateTimeOpeningDay(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DATE_TIME_FORMATTER);

    if (allDay) {
      return getDateTimeOfEndDay(localDate);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
      LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
      return getDateTimeZoneRetain(LocalDateTime.of(localDate, localTime));
    }
  }

  /**
   * Get the date with the end of the day
   */
  private DateTime getDateTimeOfEndDay(LocalDate localDate) {
    return getDateTimeZoneRetain(localDate.atTime(LocalTime.MAX));
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private DateTime getThresholdDateTime(DateTime dateTime) {
    return dateTime.withSecondOfMinute(0).withMillisOfSecond(0);
  }

  private DateTime getTimeOpeningPeriodDay(List<OpeningDayPeriod> openingDayPeriods, String interval, int duration) {

    OpeningDay prevOpeningDay = openingDayPeriods.get(0).getOpeningDay();
    OpeningDay currentOpeningDay = openingDayPeriods.get(1).getOpeningDay();

    LoanPolicyPeriod period = interval.equals(INTERVAL_HOURS)
      ? LoanPolicyPeriod.HOURS
      : LoanPolicyPeriod.MINUTES;

    LocalDate dateOfCurrentDay = LocalDate.parse(currentOpeningDay.getDate(), DATE_TIME_FORMATTER);
    LocalTime timeShift = getTimeShift(LocalTime.now(ZoneOffset.UTC), period, duration);

    if (isDateTimeWithDurationInsideDay(currentOpeningDay, timeShift)) {
      if (isInPeriodOpeningDay(currentOpeningDay.getOpeningHour(), timeShift)) {
        return getDateTimeZoneRetain(LocalDateTime.of(dateOfCurrentDay, timeShift));
      }

      LocalTime endTimeOfPeriod = findEndTimeOfOpeningPeriod(currentOpeningDay.getOpeningHour(), timeShift);
      return getDateTimeZoneRetain(LocalDateTime.of(dateOfCurrentDay, endTimeOfPeriod));
    }

    LocalTime[] startAndEndTime = getStartAndEndTime(prevOpeningDay.getOpeningHour());
    LocalTime endTime = startAndEndTime[1];

    if (timeShift.isAfter(endTime)) {
      return getDateTimeZoneRetain(LocalDateTime.of(dateOfCurrentDay, endTime));
    }

    LocalDate dateOfPrevDay = LocalDate.parse(prevOpeningDay.getDate(), DATE_TIME_FORMATTER);
    LocalTime prevEndTime = getStartAndEndTime(prevOpeningDay.getOpeningHour())[1];
    return getDateTimeZoneRetain(LocalDateTime.of(dateOfPrevDay, prevEndTime));
  }

  private LocalTime findEndTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
    LocalTime endTimePeriod = LocalTime.parse(openingHoursList.get(openingHoursList.size() - 1).getEndTime());
    if (time.isAfter(endTimePeriod)) {
      return endTimePeriod;
    }

    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime endTimeFirst = LocalTime.parse(openingHoursList.get(i).getEndTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return endTimeFirst;
      }
    }
    return LocalTime.parse(openingHoursList.get(0).getEndTime());
  }

  private DateTime getDateTimeZoneRetain(LocalDateTime localDateTime) {
    return new DateTime(localDateTime.toString())
      .withZoneRetainFields(DateTimeZone.UTC);
  }

  private String createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    IndividualResource resource = loanPolicyClient.create(loanPolicyEntry);
    policiesToDelete.add(resource.getId());
    useLoanPolicyAsFallback(resource.getId());
    return resource.getId().toString();
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyOffsetTimeEntry(int duration, String intervalId) {
    JsonObject period = new JsonObject()
      .put("duration", duration)
      .put("intervalId", intervalId);

    return new JsonObject()
      .put("name", LOAN_POLICY_NAME)
      .put("description", "Full LoanPolicy")
      .put("loanable", true)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", POLICY_PROFILE_NAME)
        .put("period", period)
        .put("closedLibraryDueDateManagementId",
          DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS.getValue()))
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));
  }
}
