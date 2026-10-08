package com.bracits.ledgerservice.domain.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bracits.ledgerservice.domain.DomainConstants;
import com.bracits.ledgerservice.domain.DomainValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransferIdsTest {

  private static final UUID POSTING_ID = UUID.fromString("0192f5a4-1234-7abc-8def-0123456789ab");
  private static final UUID FREE_POSTING_ID = UUID.fromString("0192f5a4-1234-7abc-8def-012345678900");
  private static final UUID A = new UUID(0L, 1001L);
  private static final UUID B = new UUID(0L, 1002L);

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
  void legIdSetsTheLowByteToTheLegIndex(int legIndex) {
    UUID legId = TransferIds.legId(FREE_POSTING_ID, legIndex);

    assertThat(legId.getMostSignificantBits()).isEqualTo(FREE_POSTING_ID.getMostSignificantBits());
    assertThat(legId.getLeastSignificantBits())
        .isEqualTo(FREE_POSTING_ID.getLeastSignificantBits() | legIndex);
    assertThat(legId.getLeastSignificantBits() & DomainConstants.LEG_INDEX_MASK).isEqualTo(legIndex);
  }

  @Test
  void legIdOfFirstLegMatchesSpecExample() {
    assertThat(TransferIds.legId(FREE_POSTING_ID, 1))
        .isEqualTo(UUID.fromString("0192f5a4-1234-7abc-8def-012345678901"));
    assertThat(TransferIds.legId(FREE_POSTING_ID, 8))
        .isEqualTo(UUID.fromString("0192f5a4-1234-7abc-8def-012345678908"));
  }

  @Test
  void legIdsAreDistinctAndDeterministic() {
    List<UUID> ids = new ArrayList<>();
    for (int n = 1; n <= DomainConstants.MAX_LEGS; n++) {
      ids.add(TransferIds.legId(FREE_POSTING_ID, n));
    }
    assertThat(ids).doesNotHaveDuplicates().doesNotContain(FREE_POSTING_ID);
    assertThat(TransferIds.legId(FREE_POSTING_ID, 3)).isEqualTo(TransferIds.legId(FREE_POSTING_ID, 3));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 9, 255})
  void legIndexOutOfRangeIsRejected(int legIndex) {
    assertThatThrownBy(() -> TransferIds.legId(FREE_POSTING_ID, legIndex))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void lowByteCheck() {
    assertThat(TransferIds.hasFreeLegByte(FREE_POSTING_ID)).isTrue();
    assertThat(TransferIds.hasFreeLegByte(POSTING_ID)).isFalse();
    assertThat(TransferIds.hasFreeLegByte(new UUID(0L, 0x100L))).isTrue();
    assertThat(TransferIds.hasFreeLegByte(new UUID(0L, 0x101L))).isFalse();
  }

  @Test
  void postingRejectsNonZeroLowByte() {
    assertThatThrownBy(() -> new Posting(POSTING_ID, 1, 0L, List.of(leg())))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void postingRejectsMissingId() {
    assertThatThrownBy(() -> new Posting(null, 1, 0L, List.of(leg())))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void postingRejectsNegativeProduct() {
    assertThatThrownBy(() -> new Posting(FREE_POSTING_ID, -1, 0L, List.of(leg())))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void postingRejectsZeroOrTooManyLegs() {
    assertThatThrownBy(() -> new Posting(FREE_POSTING_ID, 1, 0L, List.of()))
        .isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new Posting(FREE_POSTING_ID, 1, 0L, null))
        .isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(
            () -> new Posting(FREE_POSTING_ID, 1, 0L, Collections.nCopies(DomainConstants.MAX_LEGS + 1, leg())))
        .isInstanceOf(DomainValidationException.class);
  }

  @Test
  void postingAcceptsOneToMaxLegs() {
    assertThatCode(() -> new Posting(FREE_POSTING_ID, 1, 0L, List.of(leg()))).doesNotThrowAnyException();
    Posting max = new Posting(FREE_POSTING_ID, 1, 0L, Collections.nCopies(DomainConstants.MAX_LEGS, leg()));
    assertThat(max.legCount()).isEqualTo(DomainConstants.MAX_LEGS);
  }

  @Test
  void legRejectsZeroOrMissingAccountIds() {
    assertThatThrownBy(() -> new Leg(DomainConstants.ZERO_ID, B, 1L, 10))
        .isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new Leg(A, DomainConstants.ZERO_ID, 1L, 10))
        .isInstanceOf(DomainValidationException.class);
    assertThatThrownBy(() -> new Leg(null, B, 1L, 10)).isInstanceOf(DomainValidationException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void legRejectsNonPositiveAmount(long amount) {
    assertThatThrownBy(() -> new Leg(A, B, amount, 10)).isInstanceOf(DomainValidationException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 65_536})
  void legRejectsInvalidCode(int code) {
    assertThatThrownBy(() -> new Leg(A, B, 1L, code)).isInstanceOf(DomainValidationException.class);
  }

  @Test
  void legRejectsSameAccount() {
    assertThatThrownBy(() -> new Leg(A, A, 1L, 10)).isInstanceOf(DomainValidationException.class);
  }

  private static Leg leg() {
    return new Leg(A, B, 100L, 10);
  }
}
