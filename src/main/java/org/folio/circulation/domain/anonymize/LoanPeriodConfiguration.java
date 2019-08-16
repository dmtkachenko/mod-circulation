package org.folio.circulation.domain.anonymize;

import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

import static org.folio.circulation.support.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

/**
 * Entity for loan`s configuration.
 */
public class LoanPeriodConfiguration {

  private JsonObject representation;
  private ClosingType closingType;
  private Boolean isTreatEnabled;
  private List<Interval> selectedPeriodsValues;
  private LoanPeriod loanPeriod;

  private LoanPeriodConfiguration(JsonObject representation) {
    this.representation = representation;

    this.closingType = ClosingType.from(getNestedStringProperty(representation, "closingType", "loan"));
    this.isTreatEnabled = getBooleanProperty(representation, "treatEnabled");
    this.selectedPeriodsValues = (List<Interval>) getArrayProperty(representation, "selectedPeriodsValues")
      .getList()
      .stream()
      .map(interval -> Interval.from(String.valueOf(interval)))
      .collect(Collectors.toList());
    this.loanPeriod = new LoanPeriod(getNestedIntegerProperty(representation, "loan", "duration"),
      Interval.from(getNestedStringProperty(representation, "loan", "intervalId")));
  }

  public static LoanPeriodConfiguration from(JsonObject jsonObject) {
    return new LoanPeriodConfiguration(jsonObject);
  }

  public JsonObject getRepresentation() {
    return representation;
  }

  public void setRepresentation(JsonObject representation) {
    this.representation = representation;
  }

  public ClosingType getClosingType() {
    return closingType;
  }

  public void setClosingType(ClosingType closingType) {
    this.closingType = closingType;
  }

  public Boolean getTreatEnabled() {
    return isTreatEnabled;
  }

  public void setTreatEnabled(Boolean treatEnabled) {
    isTreatEnabled = treatEnabled;
  }

  public List<Interval> getSelectedPeriodsValues() {
    return selectedPeriodsValues;
  }

  public void setSelectedPeriodsValues(List<Interval> selectedPeriodsValues) {
    this.selectedPeriodsValues = selectedPeriodsValues;
  }

  public LoanPeriod getLoanPeriod() {
    return loanPeriod;
  }

  public void setLoanPeriod(LoanPeriod loanPeriod) {
    this.loanPeriod = loanPeriod;
  }
}
