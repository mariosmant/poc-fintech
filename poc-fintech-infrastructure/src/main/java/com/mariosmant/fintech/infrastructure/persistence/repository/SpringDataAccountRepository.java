package com.mariosmant.fintech.infrastructure.persistence.repository;

import com.mariosmant.fintech.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AccountJpaEntity}.
 *
 * @author mariosmant
 * @since 1.0.0
 */
@Repository
public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

	/** Returns all accounts ordered by creation time descending. */
	java.util.List<AccountJpaEntity> findAllByOrderByCreatedAtDesc();

	/** Returns all accounts owned by the given user, ordered by creation time descending. */
	java.util.List<AccountJpaEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

	/** Looks up an account by its canonical, normalised IBAN. */
	java.util.Optional<AccountJpaEntity> findByIban(String iban);
}

