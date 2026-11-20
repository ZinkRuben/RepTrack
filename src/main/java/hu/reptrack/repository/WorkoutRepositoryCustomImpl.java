package hu.reptrack.repository;

import hu.reptrack.domain.Workout;
import hu.reptrack.domain.WorkoutExercise;
import hu.reptrack.dto.WorkoutFilter;
import hu.reptrack.dto.WorkoutSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.QueryUtils;

public class WorkoutRepositoryCustomImpl implements WorkoutRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<WorkoutSummary> search(Long userId, WorkoutFilter filter, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<WorkoutSummary> query = cb.createQuery(WorkoutSummary.class);
        Root<Workout> w = query.from(Workout.class);
        query.select(cb.construct(WorkoutSummary.class,
                w.get("id"), w.get("title"), w.get("startedAt"), w.get("endedAt"), w.get("fatigue"),
                cb.size(w.<List<WorkoutExercise>>get("exercises"))));
        query.where(predicates(cb, query, w, userId, filter));
        List<Order> orders = new ArrayList<>(QueryUtils.toOrders(pageable.getSort(), w, cb));
        orders.add(cb.desc(w.get("id"))); // stabil lapozás azonos időpontnál is
        query.orderBy(orders);

        TypedQuery<WorkoutSummary> typed = em.createQuery(query);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());
        List<WorkoutSummary> content = typed.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Workout> cw = countQuery.from(Workout.class);
        countQuery.select(cb.count(cw)).where(predicates(cb, countQuery, cw, userId, filter));
        long total = em.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Workout> w, Long userId,
                                   WorkoutFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(w.get("user").get("id"), userId));
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(w.<java.time.Instant>get("startedAt"), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThan(w.<java.time.Instant>get("startedAt"), filter.to()));
        }
        if (filter.exerciseId() != null || filter.category() != null) {
            // exists-alkérdés: az edzés egyszer szerepel akkor is, ha több gyakorlata is egyezik
            Subquery<Long> sub = query.subquery(Long.class);
            Root<WorkoutExercise> we = sub.from(WorkoutExercise.class);
            List<Predicate> inner = new ArrayList<>();
            inner.add(cb.equal(we.get("workout"), w));
            if (filter.exerciseId() != null) {
                inner.add(cb.equal(we.get("exercise").get("id"), filter.exerciseId()));
            }
            if (filter.category() != null) {
                inner.add(cb.equal(we.get("exercise").get("category"), filter.category()));
            }
            sub.select(we.<Long>get("id")).where(inner.toArray(Predicate[]::new));
            predicates.add(cb.exists(sub));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
