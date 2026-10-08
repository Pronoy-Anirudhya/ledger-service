package com.bracits.ledgerservice.api.account;

import com.bracits.ledgerservice.api.ApiConstants;
import com.bracits.ledgerservice.api.error.ProblemDetailMapper;
import com.bracits.ledgerservice.application.account.AccountService;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account creation and balances (spec 7.2). */
@RestController
@RequestMapping(ApiConstants.ACCOUNTS_PATH)
public final class AccountController {

  private final AccountService accountService;
  private final AccountApiMapper mapper;
  private final ProblemDetailMapper problems;

  public AccountController(AccountService accountService, AccountApiMapper mapper, ProblemDetailMapper problems) {
    this.accountService = accountService;
    this.mapper = mapper;
    this.problems = problems;
  }

  /** 201 CREATED · 200 EXISTS (identical) · 409 {@code ACCOUNT_CONFLICT}. */
  @PostMapping
  public ResponseEntity<Object> create(@Valid @RequestBody CreateAccountRequest request) {
    AccountCreation creation = accountService.create(mapper.toAccount(request));
    return switch (creation.status()) {
      case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(creation));
      case EXISTS -> ResponseEntity.ok(mapper.toResponse(creation));
      case CONFLICT -> problems.toResponse(problems.accountConflict(creation.accountId()));
    };
  }

  /** 200 balance · 404 {@code ACCOUNT_NOT_FOUND}. */
  @GetMapping(ApiConstants.ACCOUNT_BALANCE_SUBPATH)
  public ResponseEntity<Object> balance(@PathVariable(ApiConstants.PATH_VAR_ACCOUNT_ID) UUID accountId) {
    return accountService
        .balance(accountId)
        .<ResponseEntity<Object>>map(balance -> ResponseEntity.ok(mapper.toResponse(balance)))
        .orElseGet(() -> problems.toResponse(problems.accountNotFound(accountId)));
  }
}
