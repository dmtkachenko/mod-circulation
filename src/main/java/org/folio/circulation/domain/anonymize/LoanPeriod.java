package org.folio.circulation.domain.anonymize;

/**
 * Entity for loan`s 'duration' and 'invtervalId' configurations.
 */
public class LoanPeriod {

  private int duration;
  private Interval intervalId;

  LoanPeriod(int duration, Interval intervalId) {
    this.duration = duration;
    this.intervalId = intervalId;
  }

  public int getDuration() {
    return duration;
  }

  public Interval getIntervalId() {
    return intervalId;
  }
}
