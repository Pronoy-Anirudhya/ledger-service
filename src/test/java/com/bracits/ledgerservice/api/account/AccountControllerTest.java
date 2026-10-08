package com.bracits.ledgerservice.api.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.api.error.ErrorCode;
import com.bracits.ledgerservice.api.support.ApiFixtures;
import com.bracits.ledgerservice.api.support.ApiSliceTestConfig;
import com.bracits.ledgerservice.api.support.FakeLedgerStore;
import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.Balance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest
@Import(ApiSliceTestConfig.class)
class AccountControllerTest {

  private static final String ACCOUNT_BODY =
      """
      {"accountId":"%s","code":100,"flags":["DEBITS_MUST_NOT_EXCEED_CREDITS"],"userData64":7}"""
          .formatted(ApiFixtures.SENDER);

  @Autowired private MockMvc mvc;
  @Autowired private FakeLedgerStore store;

  @BeforeEach
  void setUp() {
    store.reset();
  }

  private ResultActions create(String body) throws Exception {
    return mvc.perform(post(ApiConstants.ACCOUNTS_PATH).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void createdIs201() throws Exception {
    store.answerAccounts(AccountCreationStatus.CREATED);

    create(ACCOUNT_BODY)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accountId").value(ApiFixtures.SENDER.toString()))
        .andExpect(jsonPath("$.status").value(AccountCreationStatus.CREATED.name()));
  }

  @Test
  void existsIs200() throws Exception {
    store.answerAccounts(AccountCreationStatus.EXISTS);

    create(ACCOUNT_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(AccountCreationStatus.EXISTS.name()));
  }

  @Test
  void conflictIs409Problem() throws Exception {
    store.answerAccounts(AccountCreationStatus.CONFLICT);

    create(ACCOUNT_BODY)
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_CONFLICT.name()))
        .andExpect(jsonPath("$.accountId").value(ApiFixtures.SENDER.toString()));
  }

  @Test
  void minimalBodyDefaultsFlagsAndUserData() throws Exception {
    create("{\"accountId\":\"%s\",\"code\":100}".formatted(ApiFixtures.SENDER)).andExpect(status().isCreated());
  }

  @Test
  void zeroCodeIs400() throws Exception {
    create("{\"accountId\":\"%s\",\"code\":0}".formatted(ApiFixtures.SENDER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
        .andExpect(jsonPath("$.errors[0].field").value("code"));
  }

  @Test
  void zeroAccountIdIs400() throws Exception {
    create("{\"accountId\":\"%s\",\"code\":100}".formatted(ApiFixtures.ZERO))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));
  }

  @Test
  void unknownFlagIs400() throws Exception {
    create("{\"accountId\":\"%s\",\"code\":100,\"flags\":[\"NOPE\"]}".formatted(ApiFixtures.SENDER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));
  }

  @Test
  void balanceIs200() throws Exception {
    store.putBalance(new Balance(ApiFixtures.SENDER, 300, 1_000, 50, 0));

    mvc.perform(get(ApiConstants.ACCOUNTS_PATH + ApiConstants.ACCOUNT_BALANCE_SUBPATH, ApiFixtures.SENDER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(ApiFixtures.SENDER.toString()))
        .andExpect(jsonPath("$.debitsPosted").value(300))
        .andExpect(jsonPath("$.creditsPosted").value(1_000))
        .andExpect(jsonPath("$.debitsPending").value(50))
        .andExpect(jsonPath("$.creditsPending").value(0))
        .andExpect(jsonPath("$.available").value(650));
  }

  @Test
  void balanceOfUnknownAccountIs404() throws Exception {
    mvc.perform(get(ApiConstants.ACCOUNTS_PATH + ApiConstants.ACCOUNT_BALANCE_SUBPATH, ApiFixtures.RECEIVER))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCOUNT_NOT_FOUND.name()))
        .andExpect(jsonPath("$.accountId").value(ApiFixtures.RECEIVER.toString()));
  }
}
