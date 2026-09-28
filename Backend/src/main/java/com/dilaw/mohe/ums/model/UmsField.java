package com.dilaw.mohe.ums.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Getter
@Setter
@Entity
@DiscriminatorValue("FIELD")
public class UmsField extends UmsResource {

    // JSON Property aliases for frontend compatibility
    @JsonProperty("fieldCode")
    public String getFieldCode() {
        return getResourceCode();
    }

    public void setFieldCode(String code) {
        setResourceCode(code);
    }

    @JsonProperty("fieldName")
    public String getFieldName() {
        return getResourceName();
    }

    public void setFieldName(String name) {
        setResourceName(name);
    }

    @JsonProperty("fieldNameAr")
    public String getFieldNameAr() {
        return getResourceNameAr();
    }

    public void setFieldNameAr(String name) {
        setResourceNameAr(name);
    }

    /**
     * The page a request names, before it has been resolved to the parent.
     *
     * <p>{@code pageId} used to be a getter with no setter, which makes it
     * read-only to Jackson: the id the Add dialog posted was discarded, the field
     * was saved with no parent, and generating its permissions then dereferenced
     * a null page — so creating a field was a 500 every time. The column behind
     * {@code parentResource} is {@code insertable = false}, so the relation is the
     * only thing that can write it; this carries the id until
     * {@code UmsPageService.createField} resolves it.
     */
    @Transient
    @JsonIgnore
    private Long requestedPageId;

    @JsonProperty("pageId")
    public Long getPageId() {
        return getParentResource() != null ? getParentResource().getId() : requestedPageId;
    }

    @JsonProperty("pageId")
    public void setPageId(Long pageId) {
        this.requestedPageId = pageId;
    }

    @JsonIgnore
    public UmsPage getPage() {
        return (UmsPage) getParentResource();
    }

    public void setPage(UmsPage page) {
        setParentResource(page);
    }
}
