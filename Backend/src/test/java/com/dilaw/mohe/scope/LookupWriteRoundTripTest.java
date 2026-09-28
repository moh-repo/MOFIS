package com.dilaw.mohe.scope;

import com.dilaw.mohe.model.MoheMain;
import com.dilaw.mohe.model.StudentGender;
import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.model.StudyWafarType;
import com.dilaw.mohe.service.MoheMainService;
import com.dilaw.mohe.service.StudentGenderService;
import com.dilaw.mohe.service.StudyCountryInService;
import com.dilaw.mohe.service.StudyWafarTypeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Create, update and delete actually reach the database on the lookup screens.
 *
 * <p>Until the 30 Aug 2026 fix, 22 of the lookup entities declared
 * {@code @GeneratedValue(IDENTITY)} on id columns the baseline schema created as
 * {@code int NOT NULL DEFAULT ((0))} — not identity columns. Hibernate inserted
 * without the id, asked the driver for the generated key, got nothing back, and
 * threw {@code AssertionFailure: null identifier}. <b>Add had never worked on any
 * of them.</b> The services now assign the id themselves
 * ({@code repository.nextId()}), the idiom the bank lookups already used.
 *
 * <p>Before this class, the only create of any of these entities in the whole
 * suite was {@link LookupWriteAuthorizationTest}'s assert-denied case — refused
 * by authorization before it could reach the database, which is exactly why the
 * broken insert went unnoticed. These four representatives cover the four
 * service shapes: plain (gender), setId-on-update (study country in), save-body
 * with audit logging (mohe main), and the request-DTO pair (wafar type).
 *
 * <p>Every row this test creates carries a UUID-marked name and is deleted in a
 * {@code finally}; nothing that existed before the test is touched.
 */
@Tag("integration")
@SpringBootTest
class LookupWriteRoundTripTest {

    @Autowired private StudentGenderService genders;
    @Autowired private StudyCountryInService countries;
    @Autowired private MoheMainService mains;
    @Autowired private StudyWafarTypeService wafarTypes;

    private static String marker() {
        return "RT-TEST " + UUID.randomUUID();
    }

    /** create → id assigned → re-read → update → re-read → delete → gone. */
    private <T> void roundTrip(
            String what,
            Function<String, T> create,
            Function<T, Integer> idOf,
            Function<Integer, T> read,
            Function<T, String> nameOf,
            java.util.function.BiConsumer<Integer, String> rename,
            Consumer<Integer> delete) {

        AsUser.run(AsUser.SUPERADMIN, () -> {
            Integer id = null;
            try {
                String name = marker();
                T created = create.apply(name);
                id = idOf.apply(created);
                assertThat(id).as("%s: create must assign an id", what).isNotNull().isGreaterThan(0);

                assertThat(nameOf.apply(read.apply(id)))
                        .as("%s: the created row must be readable by its id", what).isEqualTo(name);

                String renamed = marker();
                rename.accept(id, renamed);
                assertThat(nameOf.apply(read.apply(id)))
                        .as("%s: update must persist the new name", what).isEqualTo(renamed);

                delete.accept(id);
                Integer gone = id;
                id = null; // deleted; nothing left for finally
                assertThatThrownBy(() -> read.apply(gone))
                        .as("%s: the deleted row must be gone", what)
                        .isInstanceOf(ResponseStatusException.class);
            } finally {
                if (id != null) {
                    try { delete.accept(id); } catch (RuntimeException ignored) { /* best-effort cleanup */ }
                }
            }
        });
    }

    /**
     * A code is part of a gender, not decoration: eleven report views split their
     * male and female columns on {@code STUDENT_GENDER.CODE}, and V082 made the
     * column {@code NOT NULL} because a row without one is counted in neither.
     * A round trip that omitted it would be testing an insert the screen can no
     * longer make.
     */
    private static String genderCode(String name) {
        // marker() is "RT-TEST <uuid>", so this is 38 characters — inside CODE's 50
        // and unique per call, which the unique index requires.
        return name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    @Test
    @DisplayName("student gender: the insert from the failing screen, then the full round trip")
    void studentGender() {
        roundTrip("gender",
                name -> { StudentGender g = new StudentGender(); g.setStudentGenderName(name); g.setStudentGenderNameEn(name); g.setCode(genderCode(name)); return genders.create(g); },
                StudentGender::getId,
                genders::getById,
                StudentGender::getStudentGenderName,
                (id, name) -> { StudentGender g = new StudentGender(); g.setStudentGenderName(name); g.setStudentGenderNameEn(name); g.setCode(genderCode(name)); genders.update(id, g); },
                genders::delete);
    }

    @Test
    @DisplayName("study country in: the same round trip on the attaché-country lookup")
    void studyCountryIn() {
        roundTrip("study country in",
                name -> { StudyCountryIn c = new StudyCountryIn(); c.setStudyCountryInName(name); c.setStudyCountryInNameEn(name); return countries.create(c); },
                StudyCountryIn::getId,
                countries::getById,
                StudyCountryIn::getStudyCountryInName,
                (id, name) -> { StudyCountryIn c = new StudyCountryIn(); c.setStudyCountryInName(name); c.setStudyCountryInNameEn(name); countries.update(id, c); },
                countries::delete);
    }

    @Test
    @DisplayName("mohe main: the service whose update also writes the audit log")
    void moheMain() {
        roundTrip("mohe main",
                name -> { MoheMain m = new MoheMain(); m.setMoheMainName(name); m.setMoheMainNameEn(name); return mains.create(m); },
                MoheMain::getId,
                mains::getById,
                MoheMain::getMoheMainName,
                (id, name) -> { MoheMain m = new MoheMain(); m.setMoheMainName(name); m.setMoheMainNameEn(name); mains.update(id, m); },
                mains::delete);
    }

    @Test
    @DisplayName("study wafar type: its own page, its own controller, the same defect")
    void studyWafarType() {
        roundTrip("wafar type",
                name -> { StudyWafarType w = new StudyWafarType(); w.setWafarName(name); w.setWafarNameEn(name); return wafarTypes.create(w); },
                StudyWafarType::getId,
                wafarTypes::getById,
                StudyWafarType::getWafarName,
                (id, name) -> { StudyWafarType w = new StudyWafarType(); w.setWafarName(name); w.setWafarNameEn(name); wafarTypes.update(id, w); },
                wafarTypes::delete);
    }

    @Test
    @DisplayName("two creates in a row take distinct ids — the nextId contract")
    void consecutiveCreatesGetDistinctIds() {
        AsUser.run(AsUser.SUPERADMIN, () -> {
            Integer first = null, second = null;
            try {
                String nameA = marker(), nameB = marker();
                StudentGender a = new StudentGender(); a.setStudentGenderName(nameA); a.setCode(genderCode(nameA));
                StudentGender b = new StudentGender(); b.setStudentGenderName(nameB); b.setCode(genderCode(nameB));
                first = genders.create(a).getId();
                second = genders.create(b).getId();
                assertThat(second).as("MAX+1 must move on after an insert").isNotEqualTo(first).isGreaterThan(first);
            } finally {
                if (first != null) { try { genders.delete(first); } catch (RuntimeException ignored) { } }
                if (second != null) { try { genders.delete(second); } catch (RuntimeException ignored) { } }
            }
        });
    }
}
