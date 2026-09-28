package com.dilaw.mohe.accounting.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * A level within a segment: باب، بند، نوع، مجموعة، حساب.
 *
 * <p>These are rows rather than an enum on purpose. The Executive Regulation of
 * the Financial Law states that it defines the levels' "meanings, natures and
 * names" ({@code تحدد اللائحة التنفيذية لهذا القانون مدلولاتها وطبيعتها ومسمياتها}),
 * so they are the Regulation's to change, not the code's.
 */
@Getter
@Setter
@Entity
@Table(name = "COA_LEVEL", schema = "FINCORE")
public class CoaLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LEVEL_ID", nullable = false)
    private Integer id;

    @Column(name = "SEGMENT_ID", nullable = false)
    private Integer segmentId;

    @Column(name = "LEVEL_NO", nullable = false)
    private Short levelNo;

    @Nationalized
    @Column(name = "NAME_AR", nullable = false, length = 100)
    private String nameAr;

    @Nationalized
    @Column(name = "NAME_EN", nullable = false, length = 100)
    private String nameEn;

    /**
     * How many characters a code at this level carries, or {@code null} when the
     * width has not been taken from the Regulation yet.
     *
     * <p>Null means "do not validate". It is seeded null deliberately: a guessed
     * width would reject the real manual on import, and a width is as much part
     * of the Regulation as a code value is.
     */
    @Column(name = "CODE_LENGTH")
    private Short codeLength;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean active = true;
}
