package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Entity
@DiscriminatorValue("PAGE")
public class UmsPage extends UmsResource {

    @OneToMany(mappedBy = "parentResource", cascade = CascadeType.ALL, fetch = FetchType.LAZY, targetEntity = UmsPage.class)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Set<UmsPage> childPages = new HashSet<>();

    // Relationships
    @OneToMany(mappedBy = "parentResource", cascade = CascadeType.ALL, fetch = FetchType.LAZY, targetEntity = UmsPageSection.class)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Set<UmsPageSection> sections = new HashSet<>();

    @OneToMany(mappedBy = "parentResource", cascade = CascadeType.ALL, fetch = FetchType.LAZY, targetEntity = UmsField.class)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Set<UmsField> fields = new HashSet<>();

    // JSON Property aliases for frontend compatibility
    @JsonProperty("pageCode")
    public String getPageCode() {
        return getResourceCode();
    }

    public void setPageCode(String code) {
        setResourceCode(code);
    }

    @JsonProperty("pageName")
    public String getPageName() {
        return getResourceName();
    }

    public void setPageName(String name) {
        setResourceName(name);
    }

    @JsonProperty("pageNameAr")
    public String getPageNameAr() {
        return getResourceNameAr();
    }

    public void setPageNameAr(String name) {
        setResourceNameAr(name);
    }

    @JsonProperty("parentPageId")
    public Long getParentPageId() {
        return getParentResourceId();
    }

    @JsonProperty("parentPageId")
    public void setParentPageId(Long parentPageId) {
        if (parentPageId == null) {
            setParentResource(null);
        } else {
            UmsPage parentRef = new UmsPage();
            parentRef.setId(parentPageId);
            setParentResource(parentRef);
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public UmsPage getParentPage() {
        return (UmsPage) getParentResource();
    }

    public void setParentPage(UmsPage page) {
        setParentResource(page);
    }
}
