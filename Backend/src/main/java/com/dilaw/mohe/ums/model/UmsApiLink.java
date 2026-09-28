package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@DiscriminatorValue("API")
@SecondaryTable(name = "ApiLinks", schema = "UMSCORE", pkJoinColumns = @PrimaryKeyJoinColumn(name = "Id"))
public class UmsApiLink extends UmsResource {

    @Column(name = "Method", nullable = false, length = 10, table = "ApiLinks")
    @Size(max = 10)
    private String method;

    @Column(name = "UrlPattern", nullable = false, length = 255, table = "ApiLinks")
    private String urlPattern;
}
