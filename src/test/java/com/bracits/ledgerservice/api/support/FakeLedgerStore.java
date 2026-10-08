package com.bracits.ledgerservice.api.support;

import com.bracits.ledgerservice.domain.account.enums.AccountCreationStatus;
import com.bracits.ledgerservice.domain.account.model.Account;
import com.bracits.ledgerservice.domain.account.model.AccountCreation;
import com.bracits.ledgerservice.domain.account.model.Balance;
import com.bracits.ledgerservice.domain.posting.model.Posting;
import com.bracits.ledgerservice.domain.posting.model.PostingLookup;
import com.bracits.ledgerservice.domain.posting.model.PostingOutcome;
import com.bracits.ledgerservice.port.out.LedgerStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * In-memory, programmable {@link LedgerStore} for slice tests. Each operation's answer is a
 * function the test sets; {@link #blockPostings()} makes {@link #createLinked} wait until
 * {@link #release()}.
 */
public final class FakeLedgerStore implements LedgerStore {

  private static final long BLOCK_TIMEOUT_SECONDS = 10;

  private volatile Function<Posting, PostingOutcome> postingAnswer;
  private volatile Function<UUID, PostingLookup> lookupAnswer;
  private volatile Function<Account, AccountCreationStatus> accountAnswer;
  private final Map<UUID, Balance> balances = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<Posting> postings = new CopyOnWriteArrayList<>();
  private volatile CountDownLatch entered;
  private volatile CountDownLatch gate;

  public FakeLedgerStore() {
    reset();
  }

  /**
   * Default answers: every posting POSTED, lookups NOT_FOUND, accounts CREATED, no balances.
   */
  public void reset() {
    postingAnswer = posting -> new PostingOutcome.Posted(1L, false);
    lookupAnswer = PostingLookup::notFound;
    accountAnswer = account -> AccountCreationStatus.CREATED;
    balances.clear();
    postings.clear();
    entered = null;
    gate = null;
  }

  public void answerPostings(PostingOutcome outcome) {
    postingAnswer = posting -> outcome;
  }

  public void answerPostings(Function<Posting, PostingOutcome> answer) {
    postingAnswer = answer;
  }

  public void answerLookups(Function<UUID, PostingLookup> answer) {
    lookupAnswer = answer;
  }

  public void answerAccounts(AccountCreationStatus status) {
    accountAnswer = account -> status;
  }

  public void putBalance(Balance balance) {
    balances.put(balance.accountId(), balance);
  }

  /**
   * Postings received, in order.
   */
  public List<Posting> postings() {
    return List.copyOf(postings);
  }

  /**
   * From now on {@link #createLinked} blocks until {@link #release()}. Returns a latch counted down
   * on entry.
   */
  public CountDownLatch blockPostings() {
    entered = new CountDownLatch(1);
    gate = new CountDownLatch(1);
    return entered;
  }

  public void release() {
    CountDownLatch current = gate;
    if (current != null) {
      current.countDown();
    }
  }

  @Override
  public PostingOutcome createLinked(Posting posting) {
    postings.add(posting);

    CountDownLatch currentEntered = entered;
    CountDownLatch currentGate = gate;
    if (currentEntered != null && currentGate != null) {
      currentEntered.countDown();

      try {
        if (!currentGate.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          throw new IllegalStateException("fake ledger store was never released");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    return postingAnswer.apply(posting);
  }

  @Override
  public PostingLookup lookup(UUID postingId, int legCount) {
    return lookupAnswer.apply(postingId);
  }

  @Override
  public AccountCreation createAccount(Account account) {
    return new AccountCreation(account.accountId(), accountAnswer.apply(account));
  }

  @Override
  public Optional<Balance> balance(UUID accountId) {
    return Optional.ofNullable(balances.get(accountId));
  }
}
