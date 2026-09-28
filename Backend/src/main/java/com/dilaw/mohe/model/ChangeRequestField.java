package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * MOHECORE.CHANGE_REQUEST_FIELD (V073): the student fields a change request
 * may edit, with the lookup list a dropdown field draws its options from.
 * {@code CODE} is the field name on the student record ({@code studentGender},
 * {@code studyCountryIn} …); {@code LOOKUP_LIST} names the portal lookup that
 * feeds it, null for a free-text field. Used by the ministry's change-request
 * review screen and the portal's "my requests" page, which both kept this map
 * in TypeScript until V073.
 */
@Getter
@Setter
@Entity
@Table(name = "CHANGE_REQUEST_FIELD", schema = "MOHECORE")
public class ChangeRequestField extends AbstractLuLookup {

    @Nationalized
    @Column(name = "LOOKUP_LIST", length = 50)
    private String lookupList;

    /**
     * TEXT, DATE, LOOKUP, EMAIL or PHONE (V115): which input the screens draw.
     * Set here as well as by the column default, because Hibernate names every
     * column in its INSERT and would otherwise send NULL.
     */
    @Nationalized
    @Column(name = "DATA_TYPE", nullable = false, length = 20)
    private String dataType = "TEXT";
}
