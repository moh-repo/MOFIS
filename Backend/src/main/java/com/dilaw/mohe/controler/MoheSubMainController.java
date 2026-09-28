package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.MoheSubMain;
import com.dilaw.mohe.service.MoheSubMainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/moheSubMain")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class MoheSubMainController {

    @Autowired
    private MoheSubMainService service;

    @GetMapping("/")
    public List<MoheSubMain> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public MoheSubMain getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping("/")
    public MoheSubMain create(@RequestBody MoheSubMain entity) {
        return service.create(entity);
    }

    @PutMapping("/{id}")
    public MoheSubMain update(@PathVariable Integer id, @RequestBody MoheSubMain entity) {
        return service.update(id, entity);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }
}