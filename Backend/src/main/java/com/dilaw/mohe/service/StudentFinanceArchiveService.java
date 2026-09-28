package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceArch;
import com.dilaw.mohe.repository.StudentFinanceArchRepository;
import com.dilaw.mohe.util.StudentFinanceLedgerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes one live finance row into STUDENT_FINANCE_ARCH without ever writing it twice.
 *
 * <p>The archive is keyed by (student, year, quarter) — V038's
 * {@code UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER}. Two flows snapshot the live table:
 * quarter create (archives the old quarter, then advances the live rows) and quarter
 * close (final snapshot of the quarter being closed). Either can run after the other,
 * and the archive may already hold the quarter from a historical load — it holds all
 * four quarters of 2024 that way. So a snapshot is an upsert: if the row for that
 * student's quarter exists it is refreshed in place, keeping its entry id; otherwise
 * it is inserted. Last writer wins, which is what "snapshot at close" means.
 *
 * <p>Joins the caller's transaction; both callers run inside one.
 */
@Service
public class StudentFinanceArchiveService {

    /** What happened to the row: {@code refreshed} is true when an existing row was updated. */
    public record Result(StudentFinanceArch row, boolean refreshed) {
    }

    @Autowired
    private StudentFinanceArchRepository studentFinanceArchRepository;

    public Result archive(StudentFinance finance, Integer moheQuarterId) {
        Optional<StudentFinanceArch> existing = findExisting(finance);
        StudentFinanceArch target = existing.orElseGet(StudentFinanceArch::new);
        StudentFinanceLedgerMapper.copyFinanceToArch(finance, moheQuarterId, target);
        // When it was archived, on a new row too (R5-23): the column default never fired.
        target.setArchievedDate(Instant.now());
        return new Result(studentFinanceArchRepository.save(target), existing.isPresent());
    }

    /**
     * {@link #archive} for a page of finance rows, each under its own
     * {@code MOHE_QUARTER_ID}: the existing rows are found in one query and written with
     * one {@code saveAll} (review Q4). Same key, same refresh rule.
     *
     * @return how many rows were written
     */
    public int archiveAll(List<StudentFinance> finances) {
        List<Integer> ids = new ArrayList<>();
        List<Integer> years = new ArrayList<>();
        for (StudentFinance f : finances) {
            if (f.getId() != null && f.getStudentFinRecievedYear() != null && f.getStudentFinRecievedMonth() != null) {
                ids.add(f.getId());
                if (!years.contains(f.getStudentFinRecievedYear())) years.add(f.getStudentFinRecievedYear());
            }
        }
        Map<String, StudentFinanceArch> existing = new HashMap<>();
        if (!ids.isEmpty()) {
            for (StudentFinanceArch a : studentFinanceArchRepository.findByStudentIdInAndStudentFinRecievedYearIn(ids, years)) {
                existing.putIfAbsent(key(a.getStudentId(), a.getStudentFinRecievedYear(), a.getStudentFinRecievedMonth()), a);
            }
        }
        List<StudentFinanceArch> targets = new ArrayList<>(finances.size());
        for (StudentFinance f : finances) {
            StudentFinanceArch found = existing.get(key(f.getId(), f.getStudentFinRecievedYear(), f.getStudentFinRecievedMonth()));
            StudentFinanceArch target = found == null ? new StudentFinanceArch() : found;
            StudentFinanceLedgerMapper.copyFinanceToArch(f, f.getMoheQuarterId(), target);
            target.setArchievedDate(Instant.now()); // new rows too (R5-23)
            targets.add(target);
        }
        studentFinanceArchRepository.saveAll(targets);
        return targets.size();
    }

    private static String key(Integer studentId, Integer year, Integer month) {
        return studentId + "|" + year + "|" + month;
    }

    private Optional<StudentFinanceArch> findExisting(StudentFinance finance) {
        if (finance.getId() == null
                || finance.getStudentFinRecievedYear() == null
                || finance.getStudentFinRecievedMonth() == null) {
            // Nothing to key on; the insert reports the NOT NULL problem, as it always has.
            return Optional.empty();
        }
        return studentFinanceArchRepository.findByStudentIdAndStudentFinRecievedYearAndStudentFinRecievedMonth(
                finance.getId(), finance.getStudentFinRecievedYear(), finance.getStudentFinRecievedMonth());
    }
}
