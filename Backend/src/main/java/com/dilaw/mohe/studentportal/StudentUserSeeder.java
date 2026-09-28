package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.studentportal.model.StudentGroup;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.model.StudentUserGroup;
import com.dilaw.mohe.studentportal.repository.StudentGroupRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserGroupRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Seeds STUDENTCORE.student_users for every student in MOHECORE.STUDENT_DATA.
 *
 * Password: the student-portal.seed-password property, for all students. There is no
 * default: it used to be "P@ssw0rd", so any environment that switched seeding on without
 * setting one gave every student the same known password (QA SEC-001). Seeding refuses
 * to run without a password, or with one the portal policy would refuse.
 * Username: STUDENT_EMAIL from STUDENT_DATA (generated as student{ID}@mohe.edu.ly if null).
 *
 * A credentials CSV is written to uploads/student-portal-credentials-{timestamp}.csv.
 *
 * Enable via property: student-portal.seed-users=true  (default false)
 * Idempotent: students already in student_users are skipped.
 */
@Component
@Order(110)
public class StudentUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StudentUserSeeder.class);
    private static final String DEFAULT_GROUP = "STUDENTS";
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    @Value("${student-portal.seed-users:false}")
    private boolean seedUsers;

    @Value("${student-portal.seed-password:}")
    private String seedPassword;

    @Autowired private StudentDataRepository studentDataRepository;
    @Autowired private StudentUserRepository studentUserRepository;
    @Autowired private StudentUserGroupRepository studentUserGroupRepository;
    @Autowired private StudentGroupRepository studentGroupRepository;

    private final BCryptPasswordEncoder encoder = com.dilaw.mohe.config.Passwords.encoder();

    @Override
    public void run(ApplicationArguments args) {
        if (!seedUsers) return;
        String problem = com.dilaw.mohe.ums.service.PasswordPolicy.problem(
                seedPassword, null, com.dilaw.mohe.ums.service.PasswordPolicy.PORTAL_MIN);
        if (problem != null) {
            log.warn("StudentUserSeeder skipped: student-portal.seed-password is not set or not acceptable ({})", problem);
            return;
        }
        try {
            // Seeding is ministry-wide work with no user behind it, so it runs at
            // startup with no tenant scope — which under the V031 policy means
            // DENY. studentDataRepository.findAll() would return nothing and this
            // would log "no students found, nothing to seed" and exit cleanly,
            // having done nothing. V037 puts the STUDENTCORE tables it writes
            // under the same policy, so the writes would fail too.
            //
            // Elevated here rather than inside doSeed because that method is
            // @Transactional: the connection is stamped when the transaction
            // opens, so an elevation inside its body comes too late.
            com.dilaw.mohe.config.tenant.TenantContext.runAsSystem(() -> {
                try {
                    doSeed();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (Exception e) {
            log.warn("StudentUserSeeder skipped (DB unavailable or error): {}", e.getMessage());
        }
    }

    /** Entry point for ApplicationRunner (uses property password). */
    @Transactional
    public void doSeed() throws IOException {
        doSeed(this.seedPassword);
    }

    /** Entry point for admin endpoint (caller supplies the password). */
    @Transactional
    public void doSeed(String overridePassword) throws IOException {
        StudentGroup defaultGroup = studentGroupRepository.findByGroupName(DEFAULT_GROUP)
                .orElseThrow(() -> new IllegalStateException("STUDENTS group not found — run STUDENTCORE_01 SQL script first"));

        List<StudentDatum> students = studentDataRepository.findAll();
        if (students.isEmpty()) {
            log.info("StudentUserSeeder: no students found, nothing to seed.");
            return;
        }

        Path uploadsDir = Paths.get("uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadsDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path csvPath = uploadsDir.resolve("student-portal-credentials-" + timestamp + ".csv");

        int created = 0;
        int skipped = 0;

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            csv.println("student_id,student_name,username,password");

            for (StudentDatum student : students) {
                if (studentUserRepository.existsByStudentId(student.getStudentId())) {
                    skipped++;
                    continue;
                }

                // Generate email if missing
                String email = student.getStudentEmail();
                if (email == null || email.isBlank()) {
                    email = "student" + student.getStudentId() + "@mohe.edu.ly";
                }

                // Fixed password for all students
                String plainPassword = (overridePassword != null && !overridePassword.isBlank())
                        ? overridePassword : seedPassword;

                StudentUser user = new StudentUser();
                user.setStudentId(student.getStudentId());
                user.setUsername(email);
                user.setPasswordHash(encoder.encode(plainPassword));
                user.setStatus(PortalAccountStatus.ACTIVE);
                user.setFailedLoginAttempts(0);
                user.setCreatedAt(Instant.now());
                user.setIsDeleted(false);
                studentUserRepository.save(user);

                // Assign to STUDENTS group
                if (!studentUserGroupRepository.existsByStudentUserIdAndGroupId(user.getStudentUserId(), defaultGroup.getGroupId())) {
                    StudentUserGroup ug = new StudentUserGroup();
                    ug.setStudentUserId(user.getStudentUserId());
                    ug.setGroupId(defaultGroup.getGroupId());
                    studentUserGroupRepository.save(ug);
                }

                // Write to CSV
                String safeName = student.getStudentName() != null
                        ? student.getStudentName().replace(",", " ")
                        : "";
                csv.printf("%d,\"%s\",\"%s\",\"%s\"%n",
                        student.getStudentId(), safeName, email, plainPassword);

                created++;
            }
        }

        log.info("StudentUserSeeder complete — created: {}, skipped (already exist): {}", created, skipped);
        log.info("Credentials file written to: {}", csvPath.toAbsolutePath());
        log.warn("SECURITY: Delete or secure {} after distributing credentials to students.", csvPath.getFileName());
    }
}
