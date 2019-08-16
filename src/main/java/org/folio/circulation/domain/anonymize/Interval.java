package org.folio.circulation.domain.anonymize;

import java.util.Arrays;
import java.util.Objects;

/**
 * Enum for the configuration`s interval  representation.
 */
public enum Interval {

  DAYS("Days"),
  WEEKS("Weeks"),
  MONTHS("Months"),
  UNKNOWN("Unknown");

  private String representation;

  Interval(String representation) {
    this.representation = representation;
  }

  public String getRepresentation() {
    return representation;
  }

  public static Interval from(String value) {
    return Arrays.stream(values())
      .filter(v -> Objects.equals(v.getRepresentation(), (value)))
      .findFirst()
      .orElse(UNKNOWN);
  }
}
