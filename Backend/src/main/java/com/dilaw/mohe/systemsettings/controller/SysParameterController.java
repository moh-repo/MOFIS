package com.dilaw.mohe.systemsettings.controller;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.systemsettings.model.SysParameter;
import com.dilaw.mohe.systemsettings.service.SysParameterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** {@code MOHECORE.SYS_PARAMETER}: read with {@code SETTINGS.VIEW}, write with {@code SETTINGS.EDIT}. */
@RestController
@RequestMapping("api/v1/sys-parameters")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class SysParameterController {

    private final SysParameterService service;

    public SysParameterController(SysParameterService service) {
        this.service = service;
    }

    @GetMapping
    public List<SysParameter> list() {
        return service.list();
    }

    @PutMapping("/{key}")
    public SysParameter update(@PathVariable String key, @RequestBody Map<String, String> body, Authentication auth) {
        return service.update(key, body.get("value"), auth == null ? null : auth.getName());
    }
}
