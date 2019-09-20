package org.folio.circulation.domain.anonymization;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.domain.anonymization.checks.AnonymizaAfterXIntervalChecker;
import org.folio.circulation.domain.anonymization.checks.AnonymizationChecker;
import org.folio.circulation.support.Result;

public class TenantAnonymizationService extends UserLoanAnonymizationService {

  TenantAnonymizationService(LoanAnonymizationHelper anonymization) {
    super(anonymization);
  }

  @Override
  protected List<AnonymizationChecker> getAnonymizationCheckers() {
    return super.getAnonymizationCheckers().addAll(Arrays.asList(

        new AnonymizaAfterXIntervalChecker(anonymiza)

    ));
  }
}
