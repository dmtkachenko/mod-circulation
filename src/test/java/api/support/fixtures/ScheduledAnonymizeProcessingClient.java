package api.support.fixtures;

import static api.support.APITestContext.circulationModuleUrl;
import static api.support.RestAssuredClient.manuallyStartTimedTask;

import java.net.URL;

public class ScheduledAnonymizeProcessingClient {

  public void runAnonymizeProcessing(){
    URL url = circulationModuleUrl("/circulation/scheduled-anonymize-processing");
    manuallyStartTimedTask(url, 204, "scheduled-anonymize-processing");
  }
}
