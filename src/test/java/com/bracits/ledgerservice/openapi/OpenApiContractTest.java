package com.bracits.ledgerservice.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.api.support.ApiSliceTestConfig;
import com.bracits.ledgerservice.config.ConfigConstants;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contract test: every implemented {@code /internal/**} operation is in {@code openapi/ledger-api.yaml}
 * and every documented {@code /internal/**} operation is implemented (test profile, so fundings are
 * mapped). Also checks the contract is served at {@code GET /openapi.yaml}.
 */
@WebMvcTest
@Import(ApiSliceTestConfig.class)
@ActiveProfiles(ConfigConstants.TEST_PROFILE)
class OpenApiContractTest {

  private static final String CONTRACT = "openapi/ledger-api.yaml";
  private static final String SERVED_CONTRACT = "/openapi.yaml";
  private static final String HANDLER_MAPPING = "requestMappingHandlerMapping";

  @Autowired
  @Qualifier(HANDLER_MAPPING)
  private RequestMappingHandlerMapping handlerMapping;

  @Autowired private MockMvc mvc;

  @Test
  void implementedOperationsMatchTheContract() {
    assertThat(implementedOperations()).isNotEmpty().isEqualTo(documentedOperations());
  }

  @Test
  void contractIsServedAsStaticFile() throws Exception {
    mvc.perform(get(SERVED_CONTRACT)).andExpect(status().isOk());
  }

  private Set<String> implementedOperations() {
    Set<String> operations = new TreeSet<>();
    for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
      for (String path : info.getPatternValues()) {
        if (path.startsWith(ApiConstants.INTERNAL_V1)) {
          for (RequestMethod method : info.getMethodsCondition().getMethods()) {
            operations.add(method.name() + " " + path);
          }
        }
      }
    }
    return operations;
  }

  private static Set<String> documentedOperations() {
    OpenAPI openApi = new OpenAPIV3Parser().read(CONTRACT);
    Set<String> operations = new TreeSet<>();
    for (Map.Entry<String, PathItem> entry : openApi.getPaths().entrySet()) {
      if (entry.getKey().startsWith(ApiConstants.INTERNAL_V1)) {
        entry
            .getValue()
            .readOperationsMap()
            .keySet()
            .forEach(method -> operations.add(method.name() + " " + entry.getKey()));
      }
    }
    return operations;
  }
}
