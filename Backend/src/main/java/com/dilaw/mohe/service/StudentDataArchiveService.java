package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataArchRepository;
import com.dilaw.mohe.util.QuarterArchiveMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes one live student row into STUDENT_DATA_ARCH without ever writing it twice.
 *
 * <p>The sibling of {@link StudentFinanceArchiveService}, and for the same reason. Two
 * flows snapshot STUDENT_DATA — quarter create (archives the period being left, then
 * advances the live rows) and quarter close (final snapshot of the quarter being
 * closed) — and either can run after the other. The finance archive has been an upsert
 * since V038 gave it a unique index; this one had nothing, and quarter close inserted
 * with a plain {@code save()}, so closing a quarter that create had already archived
 * wrote a second copy of all 941 students.
 *
 * <p>The key is <b>(STUDENT_ID, CURRENT_QUARTER)</b> — the period the snapshot is
 * <em>of</em>, not the quarter being created. That is what the 1,882 rows already in
 * the table are keyed by: 941 at 20241 and 941 at 20242, one per student per period,
 * with no duplicates inside either. It matches the finance archive's
 * (student, year, quarter) exactly.
 *
 * <p>A refresh keeps the row's id and its original {@code INSERTED_TO_ARCH_DATE}: the
 * column says when the snapshot first landed, and a later refresh does not change that.
 *
 * <p>Joins the caller's transaction; both callers run inside one.
 */
@Service
public class StudentDataArchiveService {

    /** What happened to the row: {@code refreshed} is true when an existing row was updated. */
    public record Result(StudentDataArch row, boolean refreshed) {
    }

    @Autowired
    private StudentDataArchRepository studentDataArchRepository;

    public Result archive(StudentDatum student, Integer moheQuarterId, String profileSnapshot) {
        Optional<StudentDataArch> existing = findExisting(student);
        StudentDataArch target = existing.orElseGet(StudentDataArch::new);
        Instant firstInserted = target.getInsertedToArchDate();
        QuarterArchiveMapper.copyDatumToArch(student, moheQuarterId, profileSnapshot, target);
        if (existing.isPresent() && firstInserted != null) {
            target.setInsertedToArchDate(firstInserted);
        }
        return new Result(studentDataArchRepository.save(target), existing.isPresent());
    }

    /**
     * {@link #archive} for a page of students: the existing rows are found in one query
     * and written with one {@code saveAll}, instead of a lookup and a save per student
     * (review Q4). Same key, same refresh rule.
     *
     * @param profileSnapshots full-profile JSON by student id; a missing entry archives without one
     * @return how many rows were written
     */
    public int archiveAll(List<StudentDatum> students, Integer moheQuarterId, Map<Integer, String> profileSnapshots) {
        List<Integer> ids = new ArrayList<>();
        List<String> periods = new ArrayList<>();
        for (StudentDatum s : students) {
            if (s.getStudentId() != null && s.getCurrentQuarter() != null) {
                ids.add(s.getStudentId());
                if (!periods.contains(s.getCurrentQuarter())) periods.add(s.getCurrentQuarter());
            }
        }
        Map<String, StudentDataArch> existing = new HashMap<>();
        if (!ids.isEmpty()) {
            for (StudentDataArch a : studentDataArchRepository.findByStudentIdInAndCurrentQuarterIn(ids, periods)) {
                // Lowest id wins, as findFirst...OrderByIdAsc does for a single student.
                existing.merge(key(a.getStudentId(), a.getCurrentQuarter()), a,
                        (x, y) -> x.getId() <= y.getId() ? x : y);
            }
        }
        List<StudentDataArch> targets = new ArrayList<>(students.size());
        for (StudentDatum s : students) {
            StudentDataArch found = s.getStudentId() == null || s.getCurrentQuarter() == null
                    ? null : existing.get(key(s.getStudentId(), s.getCurrentQuarter()));
            StudentDataArch target = found == null ? new StudentDataArch() : found;
            Instant firstInserted = target.getInsertedToArchDate();
            QuarterArchiveMapper.copyDatumToArch(s, moheQuarterId, profileSnapshots.get(s.getStudentId()), target);
            if (found != null && firstInserted != null) {
                target.setInsertedToArchDate(firstInserted);
            }
            targets.add(target);
        }
        studentDataArchRepository.saveAll(targets);
        return targets.size();
    }

    private static String key(Integer studentId, String period) {
        return studentId + "|" + Objects.toString(period, "");
    }

    private Optional<StudentDataArch> findExisting(StudentDatum student) {
        if (student.getStudentId() == null || student.getCurrentQuarter() == null) {
            // Nothing to key on. A student with no period recorded is archived as it
            // always was — a second snapshot of them is the lesser problem.
            return Optional.empty();
        }
        return studentDataArchRepository.findFirstByStudentIdAndCurrentQuarterOrderByIdAsc(
                student.getStudentId(), student.getCurrentQuarter());
    }
}
