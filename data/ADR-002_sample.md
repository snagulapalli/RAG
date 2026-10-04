# ADR-002: Database fetch strategy

Date: 2026-09-14

## Context

We are using JPA on this project (See ADR-001). JPA allows to use different fetching strategies: *Lazy* fetching or *Eager* fetching.

- **Lazy**: Only initialize "linked" entities or collections of entities when requested.
- **Eager**: Fetch everything always.

For lazy fetching to work, you need to be in an open transaction, which you are normally not in the web layer. Spring has a workaround for this in the form of the `spring.jpa.open-in-view` property, but this is an anti-pattern that should be avoided. For this reason, the Open Session In View has been disabled on this project.

> [!NOTE]
> Due to disabling the Open Session In View, you might get a `LazyInitializationException`. Don't enable it again, but follow the recommendations below to deal with it.

Always using eager fetching on everything is not desirable from a performance view. You would retrieve too much data always.

A good middle ground is using JPQL "join fetch" statements: Declare the associations as lazy on the entity. When a query needs information from the associations, explicitly request this via `JOIN FETCH`.

Example of setting the fetch type to Lazy:

```java
@Entity
public class Customer extends User {

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true) // (1)
    private final Set<Address> addresses;
    // ...
}
```

1. `@OneToMany` is LAZY by default.

Example of using `JOIN FETCH` in a JPQL query:

```java
@Query("SELECT c FROM Customer c LEFT JOIN FETCH c.addresses WHERE c.id = :id")
Optional<Customer> findCustomerByIdEagerly(@Param("id") UserId userId);
```

This will get the matching customer with his addresses set fully initialized, thus avoiding the N+1 query problem.

### Duplicates due to JOIN FETCH

It can happen that you get duplicates of your entity (which will usually be an aggregate root) when using `JOIN FETCH`.

In that case, it is important to use `DISTINCT` in the JPQL query (so Hibernate will filter duplicates), but avoid that the `DISTINCT` keyword is passed on to the generated SQL query (as this has a performance impact without any benefit). See "The best way to use the JPQL DISTINCT keyword with JPA and Hibernate" for more details.

To achieve this, you need to pass a query hint to Hibernate like this:

```java
@QueryHints(@QueryHint(name = "hibernate.query.passDistinctThrough", value = "false"))
@Query("SELECT DISTINCT c FROM Concert c LEFT JOIN FETCH c.studentMatches sm LEFT JOIN FETCH c.student s WHERE c.endDateTime >= :now ORDER BY c.startDateTime")
LinkedHashSet<Concert> findUpcomingConcerts(@Param("now") OffsetDateTime now);
```

To avoid having to type that property each time, there is the `@NoDistinctInSqlQueryHints` meta-annotation that can be used:

```java
@NoDistinctInSqlQueryHints
@Query("SELECT DISTINCT c FROM Concert c LEFT JOIN FETCH c.studentMatches sm LEFT JOIN FETCH c.student s WHERE c.endDateTime >= :now ORDER BY c.startDateTime")
LinkedHashSet<Concert> findUpcomingConcerts(@Param("now") OffsetDateTime now);
```

The used return type should be `Set` (if order is not important) or `LinkedHashSet` (if order is important).

## Decision

- Open Session In View is disabled via `spring.jpa.open-in-view` in `application.properties`.
- Associations in entities should be Lazy:
  - `@OneToMany` is Lazy by default.
  - `@ManyToOne` is Eager by default, so use `@ManyToOne(fetch = FetchType.LAZY)`.
- Use `JOIN FETCH` when information from the associations is needed (if you get a `LazyInitializationException`, it is needed).

## Status

ACCEPTED

## Consequences

- Entity associations stay lazy by default, so a plain `findById` never over-fetches related data.
- Any query that needs related data must explicitly use `JOIN FETCH`, making data-access costs visible in the code rather than hidden behind the Open Session In View anti-pattern.
- Developers must remember to guard `JOIN FETCH` queries returning collections with `DISTINCT` (and the `hibernate.query.passDistinctThrough=false` hint, or `@NoDistinctInSqlQueryHints`) to avoid duplicate aggregate roots without incurring a `DISTINCT` cost in the generated SQL.
- Accessing an uninitialized lazy association outside of `JOIN FETCH` will raise a `LazyInitializationException`, which is expected and should be resolved by adding the missing `JOIN FETCH` rather than re-enabling Open Session In View.
