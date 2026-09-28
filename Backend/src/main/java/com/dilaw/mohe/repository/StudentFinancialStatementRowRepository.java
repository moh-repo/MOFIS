package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.StudentFinancialStatementRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentFinancialStatementRowRepository extends JpaRepository<StudentFinancialStatementRow, String> {

    List<StudentFinancialStatementRow> findByStudentIdOrderByMoheQuarterIdAscReceivedYearAscReceivedMonthAsc(Integer studentId);
}
