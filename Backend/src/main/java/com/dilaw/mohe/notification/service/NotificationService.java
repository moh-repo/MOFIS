package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.codes.NotificationRecipientType;

import com.dilaw.mohe.codes.NotificationSendStatus;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.notification.dto.*;
import com.dilaw.mohe.notification.model.NotificationLog;
import com.dilaw.mohe.notification.model.NotificationLogCountry;
import com.dilaw.mohe.notification.model.NotificationLogRecipient;
import com.dilaw.mohe.notification.model.NotificationSettings;
import com.dilaw.mohe.notification.repository.NotificationLogCountryRepository;
import com.dilaw.mohe.notification.repository.NotificationLogRecipientRepository;
import com.dilaw.mohe.notification.repository.NotificationLogRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.service.CountryNameService;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationSettingsService settingsService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationLogRepository logRepo;
    private final NotificationLogCountryRepository logCountryRepo;
    private final NotificationLogRecipientRepository logRecipientRepo;
    private final StudentDataRepository studentRepo;
    private final UmsUserRepository umsUserRepo;
    private final CountryScopeService countryScopeService;
    private final CountryNameService countryNameService;
    private final NotificationDispatchService dispatchService;

    public NotificationService(
            NotificationSettingsService settingsService,
            EmailService emailService,
            SmsService smsService,
            NotificationLogRepository logRepo,
            NotificationLogCountryRepository logCountryRepo,
            NotificationLogRecipientRepository logRecipientRepo,
            StudentDataRepository studentRepo,
            UmsUserRepository umsUserRepo,
            CountryScopeService countryScopeService,
            CountryNameService countryNameService,
            NotificationDispatchService dispatchService) {
        this.settingsService = settingsService;
        this.emailService = emailService;
        this.smsService = smsService;
        this.logRepo = logRepo;
        this.logCountryRepo = logCountryRepo;
        this.logRecipientRepo = logRecipientRepo;
        this.studentRepo = studentRepo;
        this.umsUserRepo = umsUserRepo;
        this.countryScopeService = countryScopeService;
        this.countryNameService = countryNameService;
        this.dispatchService = dispatchService;
    }

    /**
     * Queues one message on every requested channel. Each channel gets its own
     * history row under the same batch id, so the history shows e-mail, SMS and
     * WhatsApp counts and failures separately instead of one mixed total.
     */
    @Transactional
    public SendMessageResponse send(SendMessageRequest req, Integer callerUserId) {
        List<String> channels = validate(req);
        List<Integer> targetCountryIds = resolveTargetCountryIds(req);
        NotificationSettings cfg = settingsService.resolveForSend(targetCountryIds);
        requireEnabled(cfg, channels);

        String batchId = UUID.randomUUID().toString();
        List<RecipientContact> recipients = resolveRecipients(req, targetCountryIds);

        Map<Long, String> channelByLogId = new java.util.LinkedHashMap<>();
        for (String channel : channels) {
            NotificationLog logEntry = new NotificationLog();
            logEntry.setBatchId(batchId);
            logEntry.setChannel(channel);
            logEntry.setRecipientType(req.getRecipientType().toUpperCase());
            logEntry.setScope(req.getScope().toUpperCase());
            logEntry.setSubject(req.getSubject());
            logEntry.setBody(req.getBody());
            logEntry.setTotalCount(recipients.size());
            logEntry.setStatus(NotificationSendStatus.PENDING);
            logEntry.setCreatedBy(callerUserId);
            NotificationLog saved = logRepo.save(logEntry);
            persistLogCountries(saved.getId(), targetCountryIds);
            channelByLogId.put(saved.getId(), channel);
        }

        // Hand the batch to the async dispatcher only after this transaction
        // commits: the dispatcher reads and updates the log rows from another
        // thread on its own connection, and must not race an uncommitted insert.
        Runnable dispatchAll = () -> channelByLogId.forEach((logId, channel) ->
                dispatchService.dispatch(logId, channel, cfg, req.getSubject(), req.getBody(), recipients));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAll.run();
                }
            });
        } else {
            dispatchAll.run();
        }

        return new SendMessageResponse(batchId, NotificationSendStatus.PENDING,
                "Queued " + recipients.size() + " recipient(s) on " + channels.size()
                        + " channel(s). Check history for results.");
    }

    /**
     * Which channels a send to these countries could use, and whose settings row
     * would carry it — the same resolution {@link #send} makes, so the send screen
     * can grey out a channel before the user writes the message.
     */
    @Transactional(readOnly = true)
    public ChannelAvailabilityDto channelAvailability(List<Integer> countryIds) {
        SendMessageRequest probe = new SendMessageRequest();
        StudentFilterDto filter = new StudentFilterDto();
        filter.setCountryIds(countryIds);
        probe.setStudentFilter(filter);
        NotificationSettings cfg = settingsService.resolveForSend(resolveTargetCountryIds(probe));
        List<String> enabled = com.dilaw.mohe.codes.NotificationChannel.DELIVERABLE.stream()
                .filter(c -> NotificationSettingsService.isEnabled(cfg, c))
                .toList();
        return new ChannelAvailabilityDto(cfg.getCountryId(), enabled);
    }

    /** A channel switched off in the settings row that applies is refused, not sent anyway. */
    private static void requireEnabled(NotificationSettings cfg, List<String> channels) {
        List<String> off = channels.stream()
                .filter(c -> !NotificationSettingsService.isEnabled(cfg, c))
                .toList();
        if (!off.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Not enabled in the notification settings for this send: " + String.join(", ", off));
        }
    }

    public int previewCount(SendMessageRequest req) {
        validate(req);
        List<Integer> targetCountryIds = resolveTargetCountryIds(req);
        return resolveRecipients(req, targetCountryIds).size();
    }

    /**
     * The whole history the caller may see, with no filter applied.
     *
     * <p>Kept as its own name because "unfiltered" is what the country-scope
     * tests are actually asserting about, and six nulls at the call site would
     * say that far less clearly.
     */
    public Page<NotificationLogDto> getHistory(int page, int size, Integer countryId) {
        return getHistory(page, size, countryId, null, null, null, null, null, null);
    }

    /**
     * A page of the history the caller may see, narrowed by the screen's filter
     * bar.
     *
     * <p>Every filter is optional and a null one is inert, so the same query
     * serves the unfiltered list. Blank strings are treated as null: an empty
     * query parameter is the browser saying "no filter", and letting {@code ""}
     * through would compare it to a code and return nothing at all — a filter
     * that silently empties the page is worse than one that is ignored.
     *
     * <p>{@code sentTo} is compared inclusively, and the caller is expected to
     * send the last instant of the chosen day rather than its midnight, or a
     * message sent that afternoon falls outside a range that names it.
     */
    public Page<NotificationLogDto> getHistory(int page, int size, Integer countryId,
                                               String channel, String recipientType, String scopeCode,
                                               String status, Instant sentFrom, Instant sentTo) {
        CountryScope scope = countryScopeService.getScope();
        if (countryId != null) {
            countryScopeService.validateCountryAccess(countryId);
        }
        /*
         * countryId used to be auto-filled for a non-ministry caller with
         * getEffectiveCountryIdForRead(null) — the *first* of their allowed
         * countries. Two things were wrong with that. It narrowed an officer with
         * several attachés to one of them arbitrarily; and because the countryId
         * clause is an AND, it ran before the scope clause and overrode it, so a
         * ministry-wide send with no country rows was filtered out no matter what
         * the scope clause allowed.
         *
         * The filter is now applied only when the caller asks for it. Scope is the
         * job of the second clause in findScopedHistory, which is where it belongs.
         */
        // Newest first, and stated here rather than in the query: an ORDER BY in
        // the JPQL is appended to, never replaced, by the Pageable's own Sort.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Integer> allowedIds = scope.getAllowedCountryIds();
        if (allowedIds.isEmpty() && !scope.isMinistryUser()) {
            allowedIds = List.of(-1);
        }
        Page<NotificationLog> logs = logRepo.findScopedHistory(
                countryId, scope.isMinistryUser(), allowedIds,
                blankToNull(channel), blankToNull(recipientType), blankToNull(scopeCode),
                blankToNull(status), sentFrom, sentTo, pageable);
        Map<Integer, String> countryNames = countryNameService.nameMap();
        // One query for the whole page's country rows — this used to be one
        // findCountryIdsByLogId per row (1 + N queries per page).
        List<Long> pageIds = logs.getContent().stream().map(NotificationLog::getId).toList();
        Map<Long, List<Integer>> countryIdsByLog = pageIds.isEmpty()
                ? Map.of()
                : logCountryRepo.findByLogIdIn(pageIds).stream()
                        .collect(Collectors.groupingBy(NotificationLogCountry::getLogId,
                                Collectors.mapping(NotificationLogCountry::getCountryId, Collectors.toList())));
        return logs.map(entry -> toDto(entry, countryNames, countryIdsByLog));
    }

    /** A filter the caller left empty is no filter, not a code that matches nothing. */
    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Writes the outcome of a dispatched batch in its own short transaction.
     * Called by {@link NotificationDispatchService} — a cross-bean call, so the
     * transactional proxy is honoured (the old {@code dispatchAsync} was invoked
     * through {@code this.} and its annotations were silently ignored).
     */
    @Transactional
    public void recordOutcome(Long logId, int sent, int failed, String status, String errorSummary) {
        recordOutcome(logId, sent, failed, status, errorSummary, List.of());
    }

    /** As above, with one row per recipient (V140), written in the same transaction as the counts. */
    @Transactional
    public void recordOutcome(Long logId, int sent, int failed, String status, String errorSummary,
                              List<NotificationLogRecipient> recipients) {
        logRepo.findById(logId).ifPresent(entry -> {
            entry.setSentCount(sent);
            entry.setFailedCount(failed);
            entry.setStatus(status);
            entry.setErrorSummary(errorSummary);
            entry.setSentAt(Instant.now());
            logRepo.save(entry);
            if (recipients != null && !recipients.isEmpty()) {
                recipients.forEach(r -> r.setLogId(logId));
                logRecipientRepo.saveAll(recipients);
            }
        });
    }

    /**
     * Who one history row's message went to (V140), under the same scope as the
     * history itself: a row the caller could not list is 404 here, not 403, so an
     * id is not a way to learn that a message exists.
     *
     * <p>Empty for a batch dispatched before the recipient rows existed; the page
     * says so rather than showing an empty table.
     */
    @Transactional(readOnly = true)
    public List<NotificationRecipientDto> getRecipients(Long logId) {
        CountryScope scope = countryScopeService.getScope();
        List<Integer> allowedIds = scope.getAllowedCountryIds();
        if (allowedIds.isEmpty() && !scope.isMinistryUser()) {
            allowedIds = List.of(-1);
        }
        NotificationLog entry = logRepo.findScopedById(logId, scope.isMinistryUser(), allowedIds)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return logRecipientRepo.findByLogIdOrderByIdAsc(entry.getId()).stream()
                .map(r -> new NotificationRecipientDto(r.getId(), r.getRecipientType(), r.getRecipientId(),
                        r.getRecipientName(), r.getAddress(), r.getStatus(), r.getError(), r.getSentAt()))
                .toList();
    }

    private void persistLogCountries(Long logId, List<Integer> targetCountryIds) {
        if (targetCountryIds == null || targetCountryIds.isEmpty()) {
            return;
        }
        for (Integer countryId : targetCountryIds) {
            NotificationLogCountry row = new NotificationLogCountry();
            row.setLogId(logId);
            row.setCountryId(countryId);
            logCountryRepo.save(row);
        }
    }

    private List<Integer> resolveTargetCountryIds(SendMessageRequest req) {
        CountryScope callerScope = countryScopeService.getScope();
        List<Integer> requested = extractRequestedCountryIds(req);

        if (requested == null || requested.isEmpty()) {
            if (callerScope.isMinistryUser()) {
                return Collections.emptyList();
            }
            if (callerScope.getAllowedCountryIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No country scope assigned");
            }
            return new ArrayList<>(callerScope.getAllowedCountryIds());
        }

        if (!callerScope.isMinistryUser()) {
            for (Integer id : requested) {
                if (!callerScope.getAllowedCountryIds().contains(id)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Access denied: country not in scope: " + id);
                }
            }
        }
        return requested;
    }

    private List<Integer> extractRequestedCountryIds(SendMessageRequest req) {
        if (req.getStudentFilter() != null
                && req.getStudentFilter().getCountryIds() != null
                && !req.getStudentFilter().getCountryIds().isEmpty()) {
            return req.getStudentFilter().getCountryIds();
        }
        return Collections.emptyList();
    }

    private List<RecipientContact> resolveRecipients(SendMessageRequest req, List<Integer> targetCountryIds) {
        String type = req.getRecipientType().toUpperCase();
        if (NotificationRecipientType.STUDENT.equals(type)) {
            return resolveStudents(req, targetCountryIds);
        }
        if ("EMPLOYEE".equals(type)) {
            return resolveEmployees(req, targetCountryIds);
        }
        return List.of();
    }

    private static RecipientContact contactOf(StudentDatum s) {
        return new RecipientContact(NotificationRecipientType.STUDENT,
                s.getStudentId() == null ? null : s.getStudentId().longValue(),
                s.getStudentName(), s.getStudentEmail(), s.getStudentCellphone());
    }

    private static RecipientContact contactOf(UmsUser u) {
        return new RecipientContact(NotificationRecipientType.EMPLOYEE, u.getId(), employeeName(u),
                u.getEmail(), u.getPhone());
    }

    private List<RecipientContact> resolveStudents(SendMessageRequest req, List<Integer> targetCountryIds) {
        String scope = req.getScope().toUpperCase();
        CountryScope queryScope = buildQueryScope(targetCountryIds);

        if ("ONE".equals(scope)) {
            List<StudentDatum> matches = studentsByIdOrEmail(req).stream()
                    .filter(s -> matchesStudentFilter(s, req.getStudentFilter(), targetCountryIds))
                    .filter(s -> isStudentInScope(s, queryScope))
                    .toList();
            if (matches.size() > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Several students have this email; choose the student from the list instead");
            }
            if (matches.isEmpty()) return List.of();
            return Collections.singletonList(contactOf(matches.get(0)));
        }

        Specification<StudentDatum> spec = Specification
                .where(StudentDataSpec.countryScope(queryScope))
                .and(studentFilterSpec(req.getStudentFilter(), targetCountryIds));

        return loadStudentContacts(spec);
    }

    private Specification<StudentDatum> studentFilterSpec(StudentFilterDto filter, List<Integer> targetCountryIds) {
        return (root, query, cb) -> {
            if (filter == null && (targetCountryIds == null || targetCountryIds.isEmpty())) {
                return cb.conjunction();
            }
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (targetCountryIds != null && !targetCountryIds.isEmpty()) {
                predicates.add(root.get("studyCountryIn").in(targetCountryIds));
            } else if (filter != null && filter.getCountryIds() != null && !filter.getCountryIds().isEmpty()) {
                predicates.add(root.get("studyCountryIn").in(filter.getCountryIds()));
            }
            if (filter != null) {
                if (filter.getMajorIds() != null && !filter.getMajorIds().isEmpty()) {
                    predicates.add(root.get("studentMajorId").in(filter.getMajorIds()));
                }
                if (filter.getStudyStatusIds() != null && !filter.getStudyStatusIds().isEmpty()) {
                    predicates.add(root.get("studentStudyStatusId").in(filter.getStudyStatusIds()));
                }
                if (filter.getUniversityTypeIds() != null && !filter.getUniversityTypeIds().isEmpty()) {
                    predicates.add(root.get("studentUniversityTypeId").in(filter.getUniversityTypeIds()));
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private boolean matchesStudentFilter(StudentDatum s, StudentFilterDto filter, List<Integer> targetCountryIds) {
        if (targetCountryIds != null && !targetCountryIds.isEmpty()) {
            if (s.getStudyCountryIn() == null || !targetCountryIds.contains(s.getStudyCountryIn())) {
                return false;
            }
        }
        if (filter == null) return true;
        if (filter.getCountryIds() != null && !filter.getCountryIds().isEmpty()
                && (s.getStudyCountryIn() == null || !filter.getCountryIds().contains(s.getStudyCountryIn()))) {
            return false;
        }
        if (filter.getMajorIds() != null && !filter.getMajorIds().isEmpty()
                && (s.getStudentMajorId() == null || !filter.getMajorIds().contains(s.getStudentMajorId()))) {
            return false;
        }
        if (filter.getStudyStatusIds() != null && !filter.getStudyStatusIds().isEmpty()
                && (s.getStudentStudyStatusId() == null || !filter.getStudyStatusIds().contains(s.getStudentStudyStatusId()))) {
            return false;
        }
        if (filter.getUniversityTypeIds() != null && !filter.getUniversityTypeIds().isEmpty()
                && (s.getStudentUniversityTypeId() == null || !filter.getUniversityTypeIds().contains(s.getStudentUniversityTypeId()))) {
            return false;
        }
        return true;
    }

    private boolean isStudentInScope(StudentDatum s, CountryScope queryScope) {
        if (queryScope.isMinistryUser()) return true;
        if (s.getStudyCountryIn() == null) return false;
        return queryScope.getAllowedCountryIds().contains(s.getStudyCountryIn());
    }

    private CountryScope buildQueryScope(List<Integer> targetCountryIds) {
        CountryScope caller = countryScopeService.getScope();
        if (caller.isMinistryUser()) {
            if (targetCountryIds == null || targetCountryIds.isEmpty()) {
                return caller;
            }
            return new CountryScope(caller.getUserId(), false, caller.isAdmin(), targetCountryIds);
        }
        List<Integer> effective = (targetCountryIds == null || targetCountryIds.isEmpty())
                ? caller.getAllowedCountryIds()
                : targetCountryIds;
        return new CountryScope(caller.getUserId(), false, caller.isAdmin(), effective);
    }

    private List<RecipientContact> loadStudentContacts(Specification<StudentDatum> spec) {
        List<RecipientContact> result = new ArrayList<>();
        int page = 0;
        Page<StudentDatum> batch;
        do {
            batch = studentRepo.findAll(spec, PageRequest.of(page++, 500));
            for (StudentDatum s : batch.getContent()) {
                result.add(contactOf(s));
            }
        } while (batch.hasNext());
        return result;
    }

    private List<RecipientContact> resolveEmployees(SendMessageRequest req, List<Integer> targetCountryIds) {
        String scope = req.getScope().toUpperCase();
        List<RecipientContact> result = new ArrayList<>();

        if ("ONE".equals(scope)) {
            String email = blankToNull(req.getRecipientEmail());
            List<UmsUser> candidates = req.getRecipientId() != null
                    ? umsUserRepo.findById(req.getRecipientId().longValue()).map(List::of).orElse(List.of())
                    : email != null ? umsUserRepo.findByEmailIgnoreCaseAndIsDeletedFalse(email) : List.of();
            List<UmsUser> matches = candidates.stream()
                    .filter(u -> employeeMatchesCountryScope(u.getId(), targetCountryIds))
                    .toList();
            if (matches.size() > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Several employees have this email; choose the employee from the list instead");
            }
            matches.forEach(u -> result.add(contactOf(u)));
            return result;
        }

        List<UmsUser> users = loadEmployees(req, targetCountryIds);
        for (UmsUser u : users) {
            result.add(contactOf(u));
        }
        return result;
    }

    private List<UmsUser> loadEmployees(SendMessageRequest req, List<Integer> targetCountryIds) {
        EmployeeFilterDto filter = req.getEmployeeFilter();
        List<UmsUser> base;
        if (filter != null && filter.getGroupIds() != null && !filter.getGroupIds().isEmpty()) {
            base = umsUserRepo.findUsersByGroupIds(filter.getGroupIds());
        } else if (shouldScopeEmployees(targetCountryIds)) {
            List<Integer> ids = effectiveEmployeeCountryIds(targetCountryIds);
            base = umsUserRepo.findEmployeesInCountryScope(ids);
        } else {
            base = umsUserRepo.findByIsDeletedFalse(PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        }

        if (filter != null && filter.getGroupIds() != null && !filter.getGroupIds().isEmpty()
                && shouldScopeEmployees(targetCountryIds)) {
            List<Integer> ids = effectiveEmployeeCountryIds(targetCountryIds);
            return base.stream()
                    .filter(u -> employeeMatchesCountryScope(u.getId(), ids))
                    .toList();
        }
        return base;
    }

    private boolean shouldScopeEmployees(List<Integer> targetCountryIds) {
        CountryScope caller = countryScopeService.getScope();
        return !caller.isMinistryUser() || (targetCountryIds != null && !targetCountryIds.isEmpty());
    }

    private List<Integer> effectiveEmployeeCountryIds(List<Integer> targetCountryIds) {
        if (targetCountryIds != null && !targetCountryIds.isEmpty()) {
            return targetCountryIds;
        }
        return countryScopeService.getScope().getAllowedCountryIds();
    }

    private boolean employeeMatchesCountryScope(Long userId, List<Integer> countryIds) {
        UmsUser user = umsUserRepo.findById(userId).orElse(null);
        if (user == null) return false;
        if (Boolean.TRUE.equals(user.getIsMinistryUser())) return true;
        CountryScope caller = countryScopeService.getScope();
        if (countryIds == null || countryIds.isEmpty()) {
            if (caller.isMinistryUser()) return true;
            return umsUserRepo.findEmployeesInCountryScope(caller.getAllowedCountryIds()).stream()
                    .anyMatch(u -> u.getId().equals(userId));
        }
        return umsUserRepo.findEmployeesInCountryScope(countryIds).stream()
                .anyMatch(u -> u.getId().equals(userId));
    }

    /** @return the deliverable channels the request asks for, in display order */
    private List<String> validate(SendMessageRequest req) {
        List<String> channels = requestedChannels(req);
        if (channels.contains(com.dilaw.mohe.codes.NotificationChannel.EMAIL)
                && (req.getSubject() == null || req.getSubject().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject is required for email");
        }
        if ("ONE".equals(req.getScope()) && req.getRecipientId() == null && blankToNull(req.getRecipientEmail()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "recipientId or recipientEmail is required when scope=ONE");
        }
        return channels;
    }

    /**
     * {@code channels} when given, else the legacy single {@code channel}; BOTH
     * expands to e-mail and SMS, duplicates collapse, an unknown code is a 400.
     */
    static List<String> requestedChannels(SendMessageRequest req) {
        List<String> raw = req.getChannels() != null && !req.getChannels().isEmpty()
                ? req.getChannels()
                : req.getChannel() != null ? List.of(req.getChannel()) : List.of();
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (String code : raw) {
            List<String> expanded = com.dilaw.mohe.codes.NotificationChannel.expand(code);
            if (expanded.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown channel: " + code);
            }
            wanted.addAll(expanded);
        }
        if (wanted.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "choose at least one channel: EMAIL, SMS or WHATSAPP");
        }
        return com.dilaw.mohe.codes.NotificationChannel.DELIVERABLE.stream().filter(wanted::contains).toList();
    }

    /** The student a scope=ONE send names, by id or else by the email on file. */
    private List<StudentDatum> studentsByIdOrEmail(SendMessageRequest req) {
        if (req.getRecipientId() != null) {
            return studentRepo.findById(req.getRecipientId()).map(List::of).orElse(List.of());
        }
        String email = blankToNull(req.getRecipientEmail());
        if (email == null) return List.of();
        String wanted = email.toLowerCase(Locale.ROOT);
        Specification<StudentDatum> byEmail = (root, query, cb) ->
                cb.equal(cb.lower(root.get("studentEmail")), wanted);
        return studentRepo.findAll(byEmail, PageRequest.of(0, 10)).getContent();
    }

    private static final int RECIPIENT_OPTIONS = 20;

    /**
     * The "send to one person" picker (review R4): up to 20 students or employees
     * the caller may message, matched by id, name or email. It was a bare number
     * box labelled as an identity number, so a sender had to know the internal id.
     * The caller's country scope applies, as it does to the send itself.
     */
    @Transactional(readOnly = true)
    public List<RecipientOptionDto> searchRecipients(String recipientType, String search) {
        String term = blankToNull(search);
        if (term == null) return List.of();
        Integer id = null;
        try {
            id = Integer.valueOf(term);
        } catch (NumberFormatException ignored) {
            // a name or an email
        }
        if ("EMPLOYEE".equalsIgnoreCase(recipientType)) {
            return searchEmployees(term, id);
        }
        return searchStudents(term, id);
    }

    private List<RecipientOptionDto> searchStudents(String term, Integer id) {
        String like = "%" + term.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        final Integer wantedId = id;
        Specification<StudentDatum> match = (root, query, cb) -> {
            var any = new ArrayList<jakarta.persistence.criteria.Predicate>();
            any.add(cb.like(cb.lower(root.get("studentName")), like, '\\'));
            any.add(cb.like(cb.lower(root.get("studentEmail")), like, '\\'));
            if (wantedId != null) any.add(cb.equal(root.get("studentId"), wantedId));
            return cb.or(any.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Specification<StudentDatum> inScope = StudentDataSpec.countryScope(countryScopeService.getScope());
        List<StudentDatum> found = new ArrayList<>();
        // The student whose id was typed comes first: "12" also matches every name
        // and email containing 12, and the one meant could fall off the list.
        if (wantedId != null) {
            Specification<StudentDatum> byId = (root, query, cb) -> cb.equal(root.get("studentId"), wantedId);
            found.addAll(studentRepo.findAll(Specification.where(inScope).and(byId), PageRequest.of(0, 1)).getContent());
        }
        found.addAll(studentRepo.findAll(Specification.where(inScope).and(match),
                PageRequest.of(0, RECIPIENT_OPTIONS, Sort.by("studentName"))).getContent());
        return found.stream()
                .filter(distinctById())
                .limit(RECIPIENT_OPTIONS)
                .map(s -> new RecipientOptionDto(s.getStudentId().longValue(), s.getStudentName(),
                        s.getStudentEmail(), hasText(s.getStudentEmail()), hasText(s.getStudentCellphone())))
                .toList();
    }

    private static java.util.function.Predicate<StudentDatum> distinctById() {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        return s -> seen.add(s.getStudentId());
    }

    private List<RecipientOptionDto> searchEmployees(String term, Integer id) {
        List<UmsUser> found = new ArrayList<>();
        if (id != null) {
            umsUserRepo.findById(id.longValue())
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .ifPresent(found::add);
        }
        CountryScope caller = countryScopeService.getScope();
        List<Integer> countries = caller.getAllowedCountryIds().isEmpty() ? List.of(-1) : caller.getAllowedCountryIds();
        // Scope first, then the limit (W4-09): the search used to take the first 180
        // matches and drop the unreachable ones afterwards.
        found.addAll(caller.isMinistryUser()
                ? umsUserRepo.searchUsers(term, PageRequest.of(0, RECIPIENT_OPTIONS)).getContent()
                : umsUserRepo.searchEmployeesInCountryScope(term, countries, PageRequest.of(0, RECIPIENT_OPTIONS)));
        java.util.Set<Long> reachable = caller.isMinistryUser() ? null
                : umsUserRepo.findEmployeesInCountryScope(countries).stream()
                        .map(UmsUser::getId).collect(Collectors.toSet());
        return found.stream()
                .filter(u -> reachable == null || reachable.contains(u.getId()))
                .distinct()
                .limit(RECIPIENT_OPTIONS)
                .map(u -> new RecipientOptionDto(u.getId(), employeeName(u), u.getEmail(),
                        hasText(u.getEmail()), hasText(u.getPhone())))
                .toList();
    }

    private static String employeeName(UmsUser u) {
        String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return name.isEmpty() ? u.getUserName() : name;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private NotificationLogDto toDto(NotificationLog e, Map<Integer, String> countryNames,
                                     Map<Long, List<Integer>> countryIdsByLog) {
        NotificationLogDto dto = new NotificationLogDto();
        dto.setId(e.getId());
        dto.setBatchId(e.getBatchId());
        dto.setChannel(e.getChannel());
        dto.setRecipientType(e.getRecipientType());
        dto.setScope(e.getScope());
        dto.setSubject(e.getSubject());
        dto.setBody(e.getBody());
        dto.setTotalCount(e.getTotalCount());
        dto.setSentCount(e.getSentCount());
        dto.setFailedCount(e.getFailedCount());
        dto.setStatus(e.getStatus());
        dto.setErrorSummary(e.getErrorSummary());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setSentAt(e.getSentAt());

        List<Integer> ids = countryIdsByLog.getOrDefault(e.getId(), List.of());
        dto.setCountryIds(ids);
        dto.setCountryNames(ids.stream()
                .map(id -> countryNames.getOrDefault(id, String.valueOf(id)))
                .collect(Collectors.toList()));
        return dto;
    }
}
