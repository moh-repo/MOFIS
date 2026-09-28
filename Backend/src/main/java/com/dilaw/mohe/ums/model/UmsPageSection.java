package com.dilaw.mohe.ums.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@DiscriminatorValue("SECTION")
public class UmsPageSection extends UmsResource {

    // Aliases
    public String getSectionCode() {
        return getResourceCode();
    }

    public void setSectionCode(String code) {
        setResourceCode(code);
    }

    public String getSectionName() {
        return getResourceName();
    }

    public void setSectionName(String name) {
        setResourceName(name);
    }

    public String getSectionNameAr() {
        return getResourceNameAr();
    }

    public void setSectionNameAr(String name) {
        setResourceNameAr(name);
    }

    public UmsPage getPage() {
        return (UmsPage) getParentResource();
    }

    public void setPage(UmsPage page) {
        setParentResource(page);
    }
}
