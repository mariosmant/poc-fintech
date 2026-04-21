package com.mariosmant.fintech.domain.model.vo;

/**
 * Common identity contract for all strongly-typed identifier Value Objects.
 *
 * <p>Every domain identity VO (e.g. {@link AccountId}, {@link TransferId},
 * {@link LedgerEntryId}) wraps a single underlying value — typically a
 * {@link java.util.UUID} — and exposes it through {@link #value()}.</p>
 *
 * <p>Having a single interface lets generic code (serialisers, repositories,
 * test fixtures) treat identity VOs uniformly without reflection, while still
 * preserving the <b>type safety</b> that is the whole point of introducing
 * per-aggregate identifier types in the first place: an {@code AccountId}
 * still cannot be passed where a {@code TransferId} is required.</p>
 *
 * <p>An ArchUnit fitness function (see {@code HexagonalArchitectureTest})
 * enforces that every record in {@code domain.model.vo} whose simple name
 * ends in {@code Id} implements this interface, so new identity VOs cannot
 * silently diverge from the contract.</p>
 *
 * @param <T> the underlying raw identifier type (e.g. {@link java.util.UUID})
 * @author mariosmant
 * @since 1.0.0
 */
public interface HasId<T> {

    /**
     * @return the underlying identifier value; never {@code null}
     */
    T value();
}

