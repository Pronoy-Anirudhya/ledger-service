package com.bracits.ledgerservice.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.api.account.AccountResponse;
import com.bracits.ledgerservice.api.account.BalanceResponse;
import com.bracits.ledgerservice.api.account.CreateAccountRequest;
import com.bracits.ledgerservice.api.error.ErrorCode;
import com.bracits.ledgerservice.api.funding.FundingRequest;
import com.bracits.ledgerservice.api.posting.LegRequest;
import com.bracits.ledgerservice.api.posting.PostingLookupResponse;
import com.bracits.ledgerservice.api.posting.PostingRequest;
import com.bracits.ledgerservice.api.posting.PostingResponse;
import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.account.AccountFlag;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Static checks of the authoritative contract {@code openapi/ledger-api.yaml}. */
class OpenApiSpecTest {

  /** Relative to the project directory, which Gradle uses as the test working directory. */
  private static final String SPEC_LOCATION = "openapi/ledger-api.yaml";

  private static final String SCHEMAS_PREFIX = "#/components/schemas/";
  private static final String REF = "$ref";

  private static SwaggerParseResult result;
  private static OpenAPI openApi;

  @BeforeAll
  static void parse() {
    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    result = new OpenAPIV3Parser().readLocation(SPEC_LOCATION, null, options);
    openApi = result.getOpenAPI();
  }

  @Test
  void parsesAsOpenApi31WithoutMessages() {
    assertThat(result.getMessages()).isEmpty();
    assertThat(openApi).isNotNull();
    assertThat(openApi.getOpenapi()).startsWith("3.1");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("1.0.0");
  }

  @Test
  void declaresEveryEndpointAndMethod() {
    Map<String, PathItem> paths = openApi.getPaths();

    assertThat(paths)
        .containsOnlyKeys(
            ApiConstants.ACCOUNTS_PATH,
            ApiConstants.ACCOUNTS_PATH + ApiConstants.ACCOUNT_BALANCE_SUBPATH,
            ApiConstants.POSTINGS_PATH,
            ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH,
            ApiConstants.FUNDINGS_PATH,
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/prometheus",
            "/openapi.yaml");

    assertThat(methods(paths.get(ApiConstants.ACCOUNTS_PATH))).containsExactly(PathItem.HttpMethod.POST);
    assertThat(methods(paths.get(ApiConstants.ACCOUNTS_PATH + ApiConstants.ACCOUNT_BALANCE_SUBPATH)))
        .containsExactly(PathItem.HttpMethod.GET);
    assertThat(methods(paths.get(ApiConstants.POSTINGS_PATH))).containsExactly(PathItem.HttpMethod.POST);
    assertThat(methods(paths.get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH)))
        .containsExactly(PathItem.HttpMethod.GET);
    assertThat(methods(paths.get(ApiConstants.FUNDINGS_PATH))).containsExactly(PathItem.HttpMethod.POST);
  }

  @Test
  void everyOperationHasAUniqueOperationId() {
    List<String> ids = new ArrayList<>();
    openApi
        .getPaths()
        .forEach(
            (path, item) ->
                item.readOperationsMap()
                    .forEach(
                        (method, operation) -> {
                          assertThat(operation.getOperationId())
                              .as("operationId of %s %s", method, path)
                              .isNotBlank();
                          ids.add(operation.getOperationId());
                        }));
    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  void everyRefResolves() throws IOException {
    JsonNode root = Yaml.mapper().readTree(new File(SPEC_LOCATION));
    List<String> refs = new ArrayList<>();
    collectRefs(root, refs);

    assertThat(refs).isNotEmpty();
    for (String ref : refs) {
      assertThat(ref).as("only local refs").startsWith("#/");
      assertThat(root.at(ref.substring(1)).isMissingNode()).as("unresolved %s", ref).isFalse();
    }
  }

  @Test
  void errorCodeEnumMatchesJava() {
    assertThat(enumValues(schema("ErrorCode")))
        .containsExactlyInAnyOrderElementsOf(names(ErrorCode.values(), ErrorCode::name));
  }

  @Test
  void accountFlagEnumMatchesJava() {
    assertThat(enumValues(schema("AccountFlag")))
        .containsExactlyInAnyOrderElementsOf(names(AccountFlag.values(), AccountFlag::name));
  }

  @Test
  void schemasMatchTheJavaRecordsFieldForField() {
    assertRecord("PostingRequest", PostingRequest.class);
    assertRecord("Leg", LegRequest.class);
    assertRecord("PostingResponse", PostingResponse.class);
    assertRecord("PostingLookupResponse", PostingLookupResponse.class);
    assertRecord("CreateAccountRequest", CreateAccountRequest.class);
    assertRecord("AccountResponse", AccountResponse.class);
    assertRecord("BalanceResponse", BalanceResponse.class);
    assertRecord("FundingRequest", FundingRequest.class);
  }

  @Test
  void problemDetailHasEveryExtensionMember() {
    Schema<?> problem = schema("ProblemDetail");
    assertThat(problem.getProperties())
        .containsKeys(
            "type",
            "title",
            "status",
            "detail",
            "instance",
            ApiConstants.PROBLEM_CODE,
            ApiConstants.PROBLEM_POSTING_ID,
            ApiConstants.PROBLEM_POSTING_STATUS,
            ApiConstants.PROBLEM_LEG_INDEX,
            ApiConstants.PROBLEM_ACCOUNT_ID,
            ApiConstants.PROBLEM_ERRORS);
    assertThat(problem.getRequired()).contains(ApiConstants.PROBLEM_CODE);
    assertThat(problem.getProperties().get(ApiConstants.PROBLEM_CODE).get$ref())
        .isEqualTo(SCHEMAS_PREFIX + "ErrorCode");
    assertThat(problem.getProperties().get(ApiConstants.PROBLEM_LEG_INDEX).getMaximum())
        .isEqualByComparingTo(BigDecimal.valueOf(DomainConstants.MAX_LEGS));
  }

  @Test
  void limitsMatchDomainConstants() {
    Schema<?> legs = schema("PostingRequest").getProperties().get("legs");
    assertThat(legs.getMinItems()).isEqualTo(DomainConstants.MIN_LEGS);
    assertThat(legs.getMaxItems()).isEqualTo(DomainConstants.MAX_LEGS);

    Schema<?> code = schema("TransferCode");
    assertThat(code.getMinimum()).isEqualByComparingTo(BigDecimal.valueOf(DomainConstants.MIN_CODE));
    assertThat(code.getMaximum()).isEqualByComparingTo(BigDecimal.valueOf(DomainConstants.MAX_CODE));

    Operation lookup =
        openApi.getPaths().get(ApiConstants.POSTINGS_PATH + ApiConstants.POSTING_BY_ID_SUBPATH).getGet();
    Parameter legsParam =
        lookup.getParameters().stream()
            .filter(p -> ApiConstants.QUERY_LEGS.equals(p.getName()))
            .findFirst()
            .orElseThrow();
    assertThat(legsParam.getIn()).isEqualTo("query");
    assertThat(legsParam.getRequired()).isTrue();
    assertThat(legsParam.getSchema().getMinimum())
        .isEqualByComparingTo(BigDecimal.valueOf(DomainConstants.MIN_LEGS));
    assertThat(legsParam.getSchema().getMaximum())
        .isEqualByComparingTo(BigDecimal.valueOf(DomainConstants.MAX_LEGS));
  }

  @Test
  void postingsDeclareEveryOutcome() {
    Operation post = openApi.getPaths().get(ApiConstants.POSTINGS_PATH).getPost();
    assertThat(post.getResponses()).containsOnlyKeys("200", "400", "409", "422", "500", "503");

    var unavailable = openApi.getComponents().getResponses().get("PostingUnavailable");
    assertThat(unavailable.getHeaders()).containsKey("Retry-After");
    assertThat(unavailable.getContent()).containsOnlyKeys("application/problem+json");
  }

  private static Schema<?> schema(String name) {
    Schema<?> schema = openApi.getComponents().getSchemas().get(name);
    assertThat(schema).as("schema %s", name).isNotNull();
    return schema;
  }

  private static List<String> enumValues(Schema<?> schema) {
    return schema.getEnum().stream().map(Objects::toString).toList();
  }

  private static <T> List<String> names(T[] values, Function<T, String> name) {
    return Arrays.stream(values).map(name).toList();
  }

  private static void assertRecord(String schemaName, Class<? extends Record> type) {
    List<String> components =
        Arrays.stream(type.getRecordComponents()).map(c -> c.getName()).toList();
    assertThat(schema(schemaName).getProperties())
        .as("properties of %s vs %s", schemaName, type.getSimpleName())
        .containsOnlyKeys(components);
  }

  private static List<PathItem.HttpMethod> methods(PathItem item) {
    return List.copyOf(item.readOperationsMap().keySet());
  }

  private static void collectRefs(JsonNode node, List<String> refs) {
    if (node.isObject()) {
      node.properties()
          .forEach(
              entry -> {
                if (REF.equals(entry.getKey()) && entry.getValue().isTextual()) {
                  refs.add(entry.getValue().asText());
                } else {
                  collectRefs(entry.getValue(), refs);
                }
              });
    } else if (node.isArray()) {
      node.forEach(child -> collectRefs(child, refs));
    }
  }
}
