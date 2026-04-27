package com.mariosmant.fintech.arch;

import com.mariosmant.fintech.domain.event.DomainEvent;
import com.mariosmant.fintech.domain.model.vo.HasId;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural fitness functions.
 *
 * <p>Written as plain JUnit 5 tests (not ArchUnit's own JUnit engine) so the
 * suite stays decoupled from the ArchUnit platform-engine version and we can
 * upgrade JUnit Jupiter / Platform independently.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HexagonalArchitectureTest {

    private JavaClasses importedClasses;

    @BeforeAll
    void importProductionClasses() {
        importedClasses = new ClassFileImporter()
                .importPackages("com.mariosmant.fintech");
        if (importedClasses.isEmpty()) {
            throw new IllegalStateException(
                    "ArchUnit imported zero classes — classpath scan misconfigured.");
        }
    }

    // ── 1. Hexagonal layering ────────────────────────────────────────────

    @Test
    @DisplayName("domain is pure Java — no Spring / Jakarta / Jackson / Hibernate leakage")
    void domainIsPureJava() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..",
                        "org.hibernate..",
                        "com.mariosmant.fintech.application..",
                        "com.mariosmant.fintech.infrastructure..",
                        "com.mariosmant.fintech.config..")
                .because("Domain must be pure Java.");
        rule.check(importedClasses);
    }

    @Test
    @DisplayName("application layer is framework-free")
    void applicationIsFrameworkFree() {
        ArchRule rule = noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.data..",
                        "org.springframework.boot..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..",
                        "org.hibernate..",
                        "com.mariosmant.fintech.infrastructure..",
                        "com.mariosmant.fintech.config..")
                .because("Application layer must be framework-free.");
        rule.check(importedClasses);
    }

    @Test
    @DisplayName("domain does not depend on infrastructure")
    void domainDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Dependencies point inward: infrastructure → domain, never reverse.")
                .check(importedClasses);
    }

    @Test
    @DisplayName("application does not depend on infrastructure")
    void applicationDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("Application depends on domain ports; concrete adapters live in infrastructure.")
                .check(importedClasses);
    }

    // ── 2. Controllers live only in infrastructure.web ───────────────────

    @Test
    @DisplayName("classes ending in Controller reside only in infrastructure.web.*")
    void controllersOnlyInInfrastructureWeb() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..infrastructure.web..")
                .because("REST adapters belong in the infrastructure web adapter.")
                .check(importedClasses);
    }

    // ── 3. Sealed DomainEvent hierarchy ──────────────────────────────────

    @Test
    @DisplayName("every concrete DomainEvent is a record listed in the sealed permits clause")
    void domainEventImplementationsAreRecords() {
        classes().that().areAssignableTo(DomainEvent.class)
                .and().areNotInterfaces()
                .should(beRecords())
                .because("Pattern-switch dispatch in TransferSagaOrchestrator relies on sealed records.")
                .check(importedClasses);
    }

    private static ArchCondition<JavaClass> beRecords() {
        return new ArchCondition<>("be a record") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.isRecord()) {
                    events.add(SimpleConditionEvent.violated(item,
                            "%s implements DomainEvent but is not a record".formatted(item.getName())));
                }
            }
        };
    }

    // ── 4. Identity VOs must implement HasId ─────────────────────────────

    @Test
    @DisplayName("every *Id record in domain.model.vo implements HasId")
    void idValueObjectsImplementHasId() {
        classes().that(isIdRecord())
                .should().implement(HasId.class)
                .because("Identity VOs must expose their value via HasId#value() for uniform access.")
                .check(importedClasses);
    }

    private static DescribedPredicate<JavaClass> isIdRecord() {
        return new DescribedPredicate<>("are *Id records in domain.model.vo") {
            @Override
            public boolean test(JavaClass c) {
                return c.getPackageName().equals("com.mariosmant.fintech.domain.model.vo")
                        && c.getSimpleName().endsWith("Id")
                        && c.isRecord();
            }
        };
    }

    // ── 5. Hygiene ───────────────────────────────────────────────────────

    @Test
    @DisplayName("domain and application never use System.out / System.err")
    void noSystemOutInDomainOrApplication() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .because("Use SLF4J for logging; never System.out/err in production code.")
                .check(importedClasses);
    }

    @Test
    @DisplayName("no Jackson polymorphic default typing is activated anywhere")
    void noPolymorphicDefaultTyping() {
        noClasses().should().callMethodWhere(targetMethodNamed("activateDefaultTyping"))
                .because("Polymorphic default typing is the root cause of the Jackson 2.x RCE CVE class.")
                .check(importedClasses);
    }

    // ── 6. RateLimitFilter must depend only on the port ──

    @Test
    @DisplayName("RateLimitFilter does not import Bucket4j or Caffeine directly")
    void rateLimitFilterUsesPortOnly() {
        noClasses().that().haveSimpleName("RateLimitFilter")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.bucket4j..",
                        "io.lettuce..",
                        "com.github.benmanes.caffeine..")
                .because("Filter must depend on the RateLimiter port only; "
                        + "concrete adapters live behind it.")
                .check(importedClasses);
    }

    // ── 7. Resilience4j must stay behind the port ───

    @Test
    @DisplayName("RateLimitFilter does not import Resilience4j (circuit-breaker stays in adapters)")
    void rateLimitFilterDoesNotImportResilience4j() {
        noClasses().that().haveSimpleName("RateLimitFilter")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.resilience4j..")
                .because("Circuit-breaker concerns belong in CircuitBreakingRateLimiter "
                        + "(an adapter behind the RateLimiter port), not in the filter.")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Programmatic Resilience4j CircuitBreaker API used only by rate-limit adapters")
    void onlyRateLimitAdaptersUseProgrammaticCircuitBreaker() {
        // The annotation-driven API (io.github.resilience4j.circuitbreaker.annotation.*)
        // is intentionally allowed everywhere — it is the documented mechanism for
        // FxRateAdapter / FraudDetectionAdapter (declarative). The programmatic types
        // (CircuitBreaker, CircuitBreakerConfig, CallNotPermittedException) are only
        // legitimate inside the rate-limit adapter package, where circuit
        // breaker lives behind the RateLimiter port.
        noClasses().that().resideInAPackage("com.mariosmant.fintech..")
                .and().resideOutsideOfPackage("..infrastructure.security.ratelimit..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "io.github.resilience4j.circuitbreaker.CircuitBreaker")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "io.github.resilience4j.circuitbreaker.CircuitBreakerConfig")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "io.github.resilience4j.circuitbreaker.CallNotPermittedException")
                .because("The programmatic CircuitBreaker API is an adapter-level concern"
                        + "; only CircuitBreakingRateLimiter may import it. "
                        + "The annotation-driven API stays freely usable elsewhere.")
                .check(importedClasses);
    }

    // ── 8. Tenant + IP-reputation hexagonal seams ──

    @Test
    @DisplayName("TenantResolver implementations live only in security.tenant.*")
    void tenantResolverImplementationsLiveInOnePackage() {
        classes().that().implement(
                        "com.mariosmant.fintech.infrastructure.security.tenant.TenantResolver")
                .should().resideInAPackage("com.mariosmant.fintech.infrastructure.security.tenant..")
                .because("Tenant adapters must live in the dedicated tenant package — domain "
                        + "and application layers must not see them.")
                .check(importedClasses);
    }

    @Test
    @DisplayName("BlockedIpFilter does not couple to Resilience4j or Bucket4j")
    void blockedIpFilterIsSelfContained() {
        noClasses().that().haveSimpleName("BlockedIpFilter")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.github.bucket4j..",
                        "io.lettuce..",
                        "io.github.resilience4j..")
                .because("The IP-reputation pre-filter is intentionally separate from the rate "
                        + "limiter — it must not silently couple to limiter internals.")
                .check(importedClasses);
    }

    @Test
    @DisplayName("IpReputationService implementations live only in security.reputation.*")
    void ipReputationImplementationsLiveInOnePackage() {
        classes().that().implement(
                        "com.mariosmant.fintech.infrastructure.security.reputation.IpReputationService")
                .should().resideInAPackage("com.mariosmant.fintech.infrastructure.security.reputation..")
                .because("IP-reputation adapters are a hexagonal seam; concrete "
                        + "implementations belong in the dedicated reputation package.")
                .check(importedClasses);
    }

    private static DescribedPredicate<JavaMethodCall> targetMethodNamed(String name) {
        return new DescribedPredicate<>("target method named '" + name + "'") {
            @Override
            public boolean test(JavaMethodCall call) {
                return call.getTarget().getName().equals(name);
            }
        };
    }
}




