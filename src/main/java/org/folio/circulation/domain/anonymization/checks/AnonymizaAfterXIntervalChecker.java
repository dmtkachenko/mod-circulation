package org.folio.circulation.domain.anonymization.checks;

import java.time.Period;

import org.folio.circulation.domain.Loan;

public class AnonymizaAfterXIntervalChecker implements AnonymizationChecker {


  private long secondsAfterLoanCloses;


  public AnonymizaAfterXIntervalChecker(long secondsAfterLoanCloses) {
    this.secondsAfterLoanCloses = secondsAfterLoanCloses;
  }

  @Override
  public boolean canBeAnonymized(Loan loan) {

//    Period.between()
    return false;
  }

  @Override
  public String getReason() {
    return "XIntervalHasNotPassed";
  }
}