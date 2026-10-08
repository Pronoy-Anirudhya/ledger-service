package com.bracits.ledgerservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.account.dto.BalanceResponse;
import com.bracits.ledgerservice.api.account.dto.CreateAccountRequest;
import com.bracits.ledgerservice.api.constant.ApiConstants;
import com.bracits.ledgerservice.api.error.enums.ErrorCode;
import com.bracits.ledgerservice.api.funding.dto.FundingRequest;
import com.bracits.ledgerservice.api.posting.dto.LegRequest;
import com.bracits.ledgerservice.api.posting.dto.PostingRequest;
import com.bracits.ledgerservice.api.posting.dto.PostingResponse;
import com.bracits.ledgerservice.config.constant.ConfigConstants;
import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.enums.AccountFlag;
import com.bracits.ledgerservice.domain.account.enums.SystemAccount;
import com.bracits.ledgerservice.domain.constant.DomainConstants;
import com.bracits.ledgerservice.domain.posting.enums.PostingStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end against a real single-replica TigerBeetle 0.17.9: the HTTP API, the posting path, the
 * adapter, result mapping and the chart-of-accounts bootstrap. Skipped automatically without
 * Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(ConfigConstants.TEST_PROFILE)
class TigerBeetleLedgerIT {

  private static final String IMAGE = "ghcr.io/tigerbeetle/tigerbeetle:0.17.9";
  private static final int TB_PORT = 3000;
  private static final String SECCOMP_UNCONFINED = "seccomp=unconfined";
  private static final String LISTENING_LOG = ".*listening on.*";
  private static final String FORMAT_AND_START =
      "/tigerbeetle format --cluster=0 --replica=0 --replica-count=1 --development"
          + " /tmp/0_0.tigerbeetle"
          + " && exec /tigerbeetle start --addresses=0.0.0.0:3000 --development --cache-grid=256MiB"
          + " /tmp/0_0.tigerbeetle";

  private static final int WALLET_CODE = 100;
  private static final int PRINCIPAL = 10;
  private static final int FEE = 11;
  private static final int VAT = 12;
  private static final int COMMISSION = 13;
  private static final int SEND_MONEY_PRODUCT = 1;

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> TIGERBEETLE =
      new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withCreateContainerCmdModifier(
              cmd -> {
                cmd.withEntrypoint("sh", "-c", FORMAT_AND_START);
                cmd.getHostConfig().withSecurityOpts(List.of(SECCOMP_UNCONFINED));
              })
          .withExposedPorts(TB_PORT)
          .waitingFor(
              Wait.forLogMessage(LISTENING_LOG, 1).withStartupTimeout(Duration.ofMinutes(2)));

  @DynamicPropertySource
  static void tigerBeetle(DynamicPropertyRegistry registry) {
    registry.add(
        ConfigConstants.TIGERBEETLE_PREFIX + ".addresses",
        () -> TIGERBEETLE.getHost() + ":" + TIGERBEETLE.getMappedPort(TB_PORT));
  }

  @Autowired
  private MockMvc mvc;
  @Autowired
  private JsonMapper json;

  @Test
  void bootstrapCreatedTheSystemAccountsAndReadinessIsUp() throws Exception {
    for (SystemAccount account : SystemAccount.values()) {
      balance(account.id());
    }

    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
  }

  @Test
  void createAccountIsIdempotentAndDetectsConflicts() throws Exception {
    UUID id = randomId();

    createWallet(id, 7L, status().isCreated(), AccountCreationStatus.CREATED);
    createWallet(id, 7L, status().isOk(), AccountCreationStatus.EXISTS);

    postJson(ApiConstants.ACCOUNTS_PATH, new CreateAccountRequest(id, WALLET_CODE, Set.of(), 8L))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_CONFLICT.name()));
  }

  @Test
  void fourLegPostingMovesEveryLegAtomically() throws Exception {
    UUID sender = newWallet(42L);
    UUID receiver = newWallet(77L);
    fund(sender, 100_500L);
    BalanceResponse feeBefore = balance(SystemAccount.FEE_INCOME.id());
    BalanceResponse vatBefore = balance(SystemAccount.VAT_PAYABLE.id());
    BalanceResponse commissionBefore = balance(SystemAccount.COMMISSION_PAYABLE.id());

    PostingRequest request = sendMoney(randomId(), sender, receiver, 100_000L);

    PostingResponse posted = postOk(request);

    assertThat(posted.status()).isEqualTo(PostingStatus.POSTED);
    assertThat(posted.replay()).isFalse();
    assertThat(posted.timestamp()).isPositive();
    assertThat(balance(sender).available()).isZero();
    assertThat(balance(sender).debitsPosted()).isEqualTo(100_500L);
    assertThat(balance(receiver).available()).isEqualTo(100_000L);
    assertThat(balance(SystemAccount.FEE_INCOME.id()).creditsPosted() - feeBefore.creditsPosted())
        .isEqualTo(348L);
    assertThat(balance(SystemAccount.VAT_PAYABLE.id()).creditsPosted() - vatBefore.creditsPosted())
        .isEqualTo(65L);
    assertThat(
        balance(SystemAccount.COMMISSION_PAYABLE.id()).creditsPosted()
            - commissionBefore.creditsPosted())
        .isEqualTo(87L);

    mvc.perform(
            get(ApiConstants.POSTINGS_PATH + "/" + request.postingId())
                .param(ApiConstants.QUERY_LEGS, "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PostingStatus.POSTED.name()))
        .andExpect(jsonPath("$.timestamp").value(posted.timestamp()));
  }

  @Test
  void insufficientFundsPostsNothing() throws Exception {
    UUID sender = newWallet(43L);
    UUID receiver = newWallet(78L);
    fund(sender, 1_000L);
    BalanceResponse feeBefore = balance(SystemAccount.FEE_INCOME.id());

    PostingRequest request = sendMoney(randomId(), sender, receiver, 100_000L);

    postJson(ApiConstants.POSTINGS_PATH, request)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value(ErrorCode.INSUFFICIENT_FUNDS.name()))
        .andExpect(jsonPath("$.postingStatus").value(PostingStatus.REJECTED.name()))
        .andExpect(jsonPath("$.legIndex").value(1));

    assertThat(balance(sender).available()).isEqualTo(1_000L);
    assertThat(balance(sender).debitsPosted()).isZero();
    assertThat(balance(receiver).creditsPosted()).isZero();
    assertThat(balance(SystemAccount.FEE_INCOME.id()).creditsPosted())
        .isEqualTo(feeBefore.creditsPosted());
    mvc.perform(
            get(ApiConstants.POSTINGS_PATH + "/" + request.postingId())
                .param(ApiConstants.QUERY_LEGS, "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PostingStatus.NOT_FOUND.name()));

    // TigerBeetle remembers the failed transfer id: a retry is a definitive 422 (never a 5xx).
    postJson(ApiConstants.POSTINGS_PATH, request)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value(ErrorCode.PREVIOUSLY_REJECTED.name()))
        .andExpect(jsonPath("$.legIndex").value(1));
  }

  @Test
  void identicalReplayReturnsPostedWithReplayTrue() throws Exception {
    UUID sender = newWallet(44L);
    UUID receiver = newWallet(79L);
    fund(sender, 100_500L);

    PostingRequest request = sendMoney(randomId(), sender, receiver, 100_000L);

    PostingResponse first = postOk(request);
    PostingResponse replay = postOk(request);

    assertThat(first.replay()).isFalse();
    assertThat(replay.status()).isEqualTo(PostingStatus.POSTED);
    assertThat(replay.replay()).isTrue();
    assertThat(replay.timestamp()).isEqualTo(first.timestamp());
    assertThat(balance(sender).debitsPosted()).isEqualTo(100_500L);
    assertThat(balance(receiver).creditsPosted()).isEqualTo(100_000L);
  }

  private PostingRequest sendMoney(UUID postingId, UUID sender, UUID receiver, long amount) {
    return new PostingRequest(
        postingId,
        SEND_MONEY_PRODUCT,
        42L,
        List.of(
            new LegRequest(sender, receiver, amount, PRINCIPAL),
            new LegRequest(sender, SystemAccount.FEE_INCOME.id(), 348L, FEE),
            new LegRequest(sender, SystemAccount.VAT_PAYABLE.id(), 65L, VAT),
            new LegRequest(sender, SystemAccount.COMMISSION_PAYABLE.id(), 87L, COMMISSION)));
  }

  private PostingResponse postOk(PostingRequest request) throws Exception {
    MvcResult result =
        postJson(ApiConstants.POSTINGS_PATH, request).andExpect(status().isOk()).andReturn();

    return json.readValue(result.getResponse().getContentAsString(), PostingResponse.class);
  }

  private UUID newWallet(long walletId) throws Exception {
    UUID id = randomId();
    createWallet(id, walletId, status().isCreated(), AccountCreationStatus.CREATED);

    return id;
  }

  private void createWallet(
      UUID id,
      long walletId,
      ResultMatcher expectedStatus,
      AccountCreationStatus expected)
      throws Exception {
    CreateAccountRequest request =
        new CreateAccountRequest(
            id, WALLET_CODE, Set.of(AccountFlag.DEBITS_MUST_NOT_EXCEED_CREDITS), walletId);

    postJson(ApiConstants.ACCOUNTS_PATH, request)
        .andExpect(expectedStatus)
        .andExpect(jsonPath("$.status").value(expected.name()));
  }

  private void fund(UUID account, long amount) throws Exception {
    FundingRequest request = new FundingRequest(randomId(), account, amount);

    postJson(ApiConstants.FUNDINGS_PATH, request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PostingStatus.POSTED.name()));
  }

  private BalanceResponse balance(UUID account) throws Exception {
    MvcResult result =
        mvc.perform(get(ApiConstants.ACCOUNTS_PATH + "/" + account + "/balance"))
            .andExpect(status().isOk())
            .andReturn();

    return json.readValue(result.getResponse().getContentAsString(), BalanceResponse.class);
  }

  private ResultActions postJson(String path, Object body) throws Exception {
    return mvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
  }

  /**
   * Random 128-bit id with the low byte cleared (free for the leg index).
   */
  private static UUID randomId() {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    return new UUID(random.nextLong(), random.nextLong() & ~DomainConstants.LEG_INDEX_MASK);
  }
}
