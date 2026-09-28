package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.MoheMain;
import com.dilaw.mohe.service.MoheMainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/moheMain")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class MoheMainController {

    @Autowired
    private MoheMainService service;

    @GetMapping("/")
    public List<MoheMain> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public MoheMain getById(@PathVariable Integer id) {
        return service.getById(id);
    }

    @PostMapping("/")
    public MoheMain create(@RequestBody MoheMain entity) {
        return service.create(entity);
    }

    @PutMapping("/{id}")
    public MoheMain update(@PathVariable Integer id, @RequestBody MoheMain entity) {
        return service.update(id, entity);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        service.delete(id);
    }
}