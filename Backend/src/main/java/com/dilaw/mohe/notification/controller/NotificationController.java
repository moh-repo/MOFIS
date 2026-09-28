package com.dilaw.mohe.notification.controller;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.notification.dto.*;
import com.dilaw.mohe.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("api/v1/notifications")
public class NotificationController {

    private final NotificationService service;
    private final com.dilaw.mohe.ums.service.UmsGroupService groupService;

    public NotificationController(NotificationService service, com.dilaw.mohe.ums.service.UmsGroupService groupService) {
        this.service = service;
        this.groupService = groupService;
    }

    /** A staff group a message may be addressed to: its id and names. */
    public record GroupOption(Long id, String name, String nameAr) {
    }

    /**
     * The staff groups a message may be addressed to (live QA M6). The send page read
     * the staff-administration group list, which a sender need not hold, and that one
     * refusal failed the page's whole lookup load. Names only, under the send permission.
     */
    @GetMapping("/recipient-groups")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SEND')")
    public ResponseEntity<List<GroupOption>> recipientGroups() {
        return ResponseEntity.ok(groupService.getAllActiveGroups().stream()
                .map(g -> new GroupOption(g.getId(), g.getGroupName(), g.getGroupNameAr()))
                .toList());
    }

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SEND')")
    public ResponseEntity<SendMessageResponse> send(@Valid @RequestBody SendMessageRequest req) {
        int userId = (int) SecurityRequestUtils.currentUserIdOrDefault(1L);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(req, userId));
    }

    @PostMapping("/preview-count")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SEND')")
    public ResponseEntity<PreviewCountResponse> previewCount(@RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(new PreviewCountResponse(service.previewCount(req)));
    }

    /**
     * The channels a send to these countries could use (switched on in the
     * settings row that applies). No countries means what the caller's own
     * scope resolves to, exactly as a send with no country filter does.
     */
    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SEND')")
    public ResponseEntity<ChannelAvailabilityDto> channelAvailability(
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(service.channelAvailability(countryIds));
    }

    /** Picker for "send to one person": students or employees by id, name or email (review R4). */
    @GetMapping("/recipients")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.SEND')")
    public ResponseEntity<List<RecipientOptionDto>> searchRecipients(
            @RequestParam(defaultValue = "STUDENT") String type,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.searchRecipients(type, q));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.VIEW')")
    public ResponseEntity<Page<NotificationLogDto>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String recipientType,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant sentFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant sentTo) {
        // All six are optional and additive, so a caller that sends none — every
        // caller before the filter bar existed — gets the list it always got.
        return ResponseEntity.ok(service.getHistory(page, size, countryId,
                channel, recipientType, scope, status, sentFrom, sentTo));
    }

    /**
     * Who one history row's message went to, one row per recipient (V140), under
     * the same scope as the history. The history page's view reads it.
     */
    @GetMapping("/history/{id}/recipients")
    @PreAuthorize("hasAuthority('NOTIFICATIONS.VIEW')")
    public ResponseEntity<List<NotificationRecipientDto>> getRecipients(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRecipients(id));
    }
}
