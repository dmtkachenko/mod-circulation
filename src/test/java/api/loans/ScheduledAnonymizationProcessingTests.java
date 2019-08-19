package api.loans;

import api.support.APITests;
import org.junit.Test;

public class ScheduledAnonymizationProcessingTests extends APITests {

  @Test
  public void testAnonymizeProcessing(){
    scheduledAnonymizeProcessingClient.runAnonymizeProcessing();
  }
}
