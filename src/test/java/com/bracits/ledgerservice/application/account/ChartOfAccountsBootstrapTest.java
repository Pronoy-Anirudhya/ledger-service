package com.bracits.ledgerservice.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.config.AccountsProperties;
import com.bracits.ledgerservice.domain.account.Account;
import com.bracits.ledgerservice.domain.account.AccountCreation;
import com.bracits.ledgerservice.domain.account.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.Balance;
import com.bracits.ledgerservice.domain.account.SystemAccount;
import com.bracits.ledgerservice.domain.posting.Posting;
import com.bracits.ledgerservice.domain.posting.PostingLookup;
import com.bracits.ledgerservice.domain.posting.PostingOutcome;
import com.bracits.ledgerservice.port.out.LedgerStore;
import com.bracits.ledgerservice.port.out.LedgerUnavailableException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ChartOfAccountsBootstrapTest {

  private static final Duration SHORT_INTERVAL = Duration.ofMillis(5);

  @Test
  void createsAllSystemAccounts() {
    FakeLedgerStore store = new FakeLedgerStore(account -> AccountCreationStatus.CREATED);

    bootstrap(store, Duration.ofSeconds(2)).run(new DefaultApplicationArguments());

    assertThat(store.accounts)
        .containsExactlyElementsOf(Arrays.stream(SystemAccount.values()).map(SystemAccount::toAccount).toList());
    assertThat(store.accounts).hasSize(4).allSatisfy(account -> assertThat(account.flags()).isEmpty());
  }

  @Test
  void toleratesExistingAccounts() {
    FakeLedgerStore store = new FakeLedgerStore(account -> AccountCreationStatus.EXISTS);

    bootstrap(store, Duration.ofSeconds(2)).run(new DefaultApplicationArguments());

    assertThat(store.accounts).hasSize(4);
  }

  @Test
  void failsOnConflict() {
    FakeLedgerStore store =
        new FakeLedgerStore(
            account ->
                account.accountId().equals(SystemAccount.VAT_PAYABLE.id())
                    ? AccountCreationStatus.CONFLICT
                    : AccountCreationStatus.CREATED);

    assertThatThrownBy(() -> bootstrap(store, Duration.ofSeconds(2)).run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SystemAccount.VAT_PAYABLE.name());
    assertThat(store.accounts).hasSize(2);
  }

  @Test
  void retriesWhileUnavailableThenSucceeds() {
    AtomicInteger failuresLeft = new AtomicInteger(3);
    FakeLedgerStore store =
        new FakeLedgerStore(
            account -> {
              if (failuresLeft.getAndDecrement() > 0) {
                throw new LedgerUnavailableException("down", null);
              }
              return AccountCreationStatus.CREATED;
            });

    bootstrap(store, Duration.ofSeconds(5)).run(new DefaultApplicationArguments());

    assertThat(store.attempts.get()).isEqualTo(4 + 3);
    assertThat(store.accounts).hasSize(4 + 3);
    assertThat(store.accounts.stream().distinct().toList())
        .containsExactlyElementsOf(Arrays.stream(SystemAccount.values()).map(SystemAccount::toAccount).toList());
  }

  @Test
  void failsAfterTimeoutWhileUnavailable() {
    FakeLedgerStore store =
        new FakeLedgerStore(
            account -> {
              throw new LedgerUnavailableException("down", null);
            });
    long start = System.nanoTime();

    assertThatThrownBy(() -> bootstrap(store, Duration.ofMillis(60)).run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(LedgerUnavailableException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    assertThat(store.attempts.get()).isGreaterThan(1);
  }

  private static ChartOfAccountsBootstrap bootstrap(LedgerStore store, Duration timeout) {
    return new ChartOfAccountsBootstrap(store, new AccountsProperties(true, timeout, SHORT_INTERVAL));
  }

  /** Records every createAccount call; the outcome comes from the given function. */
  private static final class FakeLedgerStore implements LedgerStore {

    private final Function<Account, AccountCreationStatus> outcome;
    private final List<Account> accounts = new CopyOnWriteArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();

    FakeLedgerStore(Function<Account, AccountCreationStatus> outcome) {
      this.outcome = outcome;
    }

    @Override
    public AccountCreation createAccount(Account account) {
      attempts.incrementAndGet();
      accounts.add(account);
      return new AccountCreation(account.accountId(), outcome.apply(account));
    }

    @Override
    public PostingOutcome createLinked(Posting posting) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PostingLookup lookup(UUID postingId, int legCount) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Balance> balance(UUID accountId) {
      throw new UnsupportedOperationException();
    }
  }
}
