package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.EnumOptionDto;
import com.dilaw.mohe.service.EnumOptionsService;
import com.dilaw.mohe.service.codelist.CodeListRegistry;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The code lists as one payload, and their editing.
 *
 * <p>{@code GET /api/v1/code-lists} returns every list (active and inactive
 * rows, both names, severity, icon) so the frontend loads them once after
 * login and resolves labels and colours synchronously. Editing
 * ({@code SETTINGS.EDIT}) is only for the {@code LU_*}-backed lists; a row
 * flagged {@code IS_SYSTEM} — one a Java constant depends on — cannot be
 * renamed, deactivated or deleted (409). The lookup screen's own controller
 * is untouched; both evict the same cache.
 */
@RestController
@RequestMapping("api/v1/code-lists")
public class CodeListController {

    private final CodeListService codeLists;

    public CodeListController(CodeListService codeLists) {
        this.codeLists = codeLists;
    }

    /** Every list, keyed by name. Any signed-in user. */
    @GetMapping
    public Map<String, List<EnumOptionDto>> all() {
        Map<String, List<EnumOptionDto>> out = new LinkedHashMap<>();
        codeLists.all().forEach((name, rows) ->
                out.put(name, rows.stream().map(r -> EnumOptionsService.toDto(r, "ar")).toList()));
        return out;
    }

    /**
     * The names of the lists, whether each is editable here, the name of its
     * extra column and the table it is served from. The table is what tells a
     * reader of the screen which rows they are actually editing — two lists can
     * read tables of near-identical names ({@code study-status} is
     * STUDENT_STUDY_STATUS, not STUDY_STATUS).
     */
    @GetMapping("/names")
    public List<Map<String, Object>> names() {
        return CodeListRegistry.names().stream()
                .map(n -> Map.<String, Object>of("name", n, "editable", CodeListRegistry.lu(n).isPresent(),
                        "extra", CodeListRegistry.lu(n).map(l -> l.extraColumn() == null ? "" : l.extraColumn()).orElse(""),
                        "table", table(n)))
                .toList();
    }

    /** The table behind a list, LU-backed or adapted. */
    private static String table(String name) {
        return CodeListRegistry.lu(name).map(CodeListRegistry.LuList::table)
                .orElseGet(() -> CodeListRegistry.adapter(name).map(CodeListRegistry.Adapter::table).orElse(""));
    }

    @GetMapping("/{list}")
    public List<EnumOptionDto> list(@PathVariable String list) {
        if (!CodeListRegistry.exists(list)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown code list: " + list);
        }
        return codeLists.rows(list).stream().map(r -> EnumOptionsService.toDto(r, "ar")).toList();
    }

    @PostMapping("/{list}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public ResponseEntity<EnumOptionDto> create(@PathVariable String list, @RequestBody CodeListService.CodeRowInput in) {
        CodeRow row = codeLists.create(list, in);
        return ResponseEntity.status(HttpStatus.CREATED).body(EnumOptionsService.toDto(row, "ar"));
    }

    @PutMapping("/{list}/{id}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public EnumOptionDto update(@PathVariable String list, @PathVariable Integer id,
                                @RequestBody CodeListService.CodeRowInput in) {
        return EnumOptionsService.toDto(codeLists.update(list, id, in), "ar");
    }

    @DeleteMapping("/{list}/{id}")
    @PreAuthorize(MoheSecurityExpressions.SETTINGS_WRITE)
    public ResponseEntity<Void> delete(@PathVariable String list, @PathVariable Integer id) {
        codeLists.delete(list, id);
        return ResponseEntity.noContent().build();
    }
}
