package kz.skillbridge.roadmap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;
import kz.skillbridge.career.CareerGoal;
import kz.skillbridge.career.CareerService;
import kz.skillbridge.career.Profession;
import kz.skillbridge.career.dto.CareerGoalDto;
import kz.skillbridge.common.ApiException;
import kz.skillbridge.gap.SkillGapService;
import kz.skillbridge.gap.dto.SkillGapItem;
import kz.skillbridge.gap.dto.SkillGapResponse;
import kz.skillbridge.learning.LearningResource;
import kz.skillbridge.learning.LearningResourceDto;
import kz.skillbridge.learning.LearningResourceRepository;
import kz.skillbridge.progress.ProgressRecorder;
import kz.skillbridge.roadmap.dto.RoadmapDto;
import kz.skillbridge.skill.SkillRepository;
import kz.skillbridge.skill.SkillSource;
import kz.skillbridge.skill.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roadmap Generator (spec §4 step 7, §10): ordered modules built from detected gaps, respecting
 * skill prerequisites, each linked to real learning resources and to vacancy demand.
 */
@Service
@RequiredArgsConstructor
public class RoadmapService {

    static final int MAX_MODULES = 6;
    static final int MAX_MODULES_WITH_PREREQS = 8;
    static final int HOURS_PER_LEVEL = 10;
    static final int RESOURCES_PER_ITEM = 4;
    static final String NOTE = "Оценка времени — ориентир (~" + HOURS_PER_LEVEL + " ч на уровень отставания), "
            + "а не измеренная длительность. Материалы — реальные ссылки, регулярно проверяемые на доступность.";

    /** skill slug → slugs that should be learned first. */
    static final Map<String, List<String>> PREREQUISITES = Map.ofEntries(
            Map.entry("pandas", List.of("python")),
            Map.entry("machine-learning", List.of("python", "statistics")),
            Map.entry("data-cleaning", List.of("pandas")),
            Map.entry("financial-reporting", List.of("accounting")),
            Map.entry("financial-analysis", List.of("accounting", "financial-reporting")),
            Map.entry("financial-modeling", List.of("excel", "financial-analysis")),
            Map.entry("valuation", List.of("financial-modeling")),
            Map.entry("budgeting", List.of("excel")),
            Map.entry("bpmn", List.of("process-modeling")),
            Map.entry("spring", List.of("java")),
            Map.entry("typescript", List.of("javascript")),
            Map.entry("react", List.of("javascript")));

    static final Map<String, String> FINAL_PROJECTS = Map.of(
            "data-analyst", "Проанализировать открытый датасет: очистка данных, SQL-запросы, дашборд и письменные выводы для бизнеса.",
            "financial-analyst", "Построить финансовую модель и DCF-оценку публичной компании по её отчётности с анализом чувствительности.",
            "business-analyst", "Описать требования к продукту: процессы AS-IS/TO-BE в BPMN, user stories с критериями приёмки, макет приоритизации.",
            "software-engineer", "Разработать REST-сервис с базой данных, тестами, историей в Git и Docker-образом.");

    private final CareerService careerService;
    private final SkillGapService gapService;
    private final RoadmapRepository roadmaps;
    private final SkillRepository skills;
    private final LearningResourceRepository resources;
    private final ProgressRecorder progress;

    @Transactional
    public RoadmapDto generate(Long userId) {
        CareerGoal goal = careerService.requirePrimaryGoal(userId);
        Profession profession = goal.getProfession();
        SkillGapResponse gap = gapService.analyse(userId, profession);
        List<SkillGapItem> modules = selectModules(gap.skills());

        roadmaps.archiveActive(userId, goal.getId());
        Roadmap roadmap = new Roadmap();
        roadmap.setUserId(userId);
        roadmap.setCareerGoal(careerService.requirePrimaryGoal(userId)); // re-read after context clear

        Map<Long, List<LearningResource>> bySkill = resources.findBySkillIdInAndCheckFailedFalseOrderByLevelAscTitleAsc(
                        modules.stream().map(m -> m.skill().id()).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getSkill().getId(), LinkedHashMap::new, Collectors.toList()));

        int position = 1;
        for (SkillGapItem m : modules) {
            RoadmapItem item = new RoadmapItem();
            item.setRoadmap(roadmap);
            item.setSkill(skills.getReferenceById(m.skill().id()));
            item.setPosition(position++);
            item.setTitle(m.skill().name() + ": " + m.currentLevel() + "/5 → " + m.requiredLevel() + "/5");
            item.setGoal("Поднять навык «" + m.skill().name() + "» до уровня " + m.requiredLevel()
                    + "/5, который ожидают работодатели для роли " + profession.getTitle() + ".");
            item.setTopics(String.join(";", skills.findById(m.skill().id()).orElseThrow().topicList()));
            item.setEstimatedHours(m.gap() * HOURS_PER_LEVEL);
            item.setCompletionCriteria("Изучить темы модуля по материалам и выполнить практическое задание по навыку «"
                    + m.skill().name() + "»; уровень навыка в профиле — не ниже " + m.requiredLevel() + "/5.");
            item.setRationale(rationale(m, gap.jobsAnalyzed(), profession.getTitle()));
            List<LearningResource> rs = bySkill.getOrDefault(m.skill().id(), List.of()).stream()
                    .sorted(Comparator.comparing((LearningResource r) -> r.getLevel() == null ? 9 : r.getLevel()))
                    .limit(RESOURCES_PER_ITEM).toList();
            item.getResources().addAll(rs);
            roadmap.getItems().add(item);
        }
        RoadmapItem finalProject = new RoadmapItem();
        finalProject.setRoadmap(roadmap);
        finalProject.setPosition(position);
        finalProject.setTitle("Итоговый практический проект");
        finalProject.setGoal(FINAL_PROJECTS.getOrDefault(profession.getSlug(),
                "Выполнить проект, объединяющий навыки из плана."));
        finalProject.setEstimatedHours(20);
        finalProject.setCompletionCriteria("Проект опубликован (например, на GitHub) и добавлен в портфолио.");
        finalProject.setRationale("Работодатели оценивают применение навыков на практике; проект станет доказательством "
                + "в портфолио.");
        roadmap.getItems().add(finalProject);

        roadmap = roadmaps.save(roadmap);
        progress.record(userId, ProgressRecorder.ROADMAP_GENERATED,
                Map.of("roadmapId", roadmap.getId(), "profession", profession.getSlug(), "modules", modules.size()));
        return toDto(roadmap);
    }

    @Transactional(readOnly = true)
    public RoadmapDto current(Long userId) {
        CareerGoal goal = careerService.requirePrimaryGoal(userId);
        return roadmaps.findFirstByUserIdAndCareerGoalIdAndStatusOrderByCreatedAtDesc(userId, goal.getId(),
                        RoadmapStatus.ACTIVE)
                .map(this::toDto)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROADMAP_NOT_FOUND",
                        "No roadmap yet — generate one"));
    }

    @Transactional
    public RoadmapDto updateItem(Long userId, Long itemId, ItemStatus status) {
        RoadmapItem item = roadmaps.findItem(itemId, userId).orElseThrow(() -> ApiException.notFound("Roadmap item"));
        if (item.getRoadmap().getStatus() == RoadmapStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, "ROADMAP_ARCHIVED", "This roadmap was replaced by a newer one");
        }
        ItemStatus from = item.getStatus();
        if (from != status) {
            item.setStatus(status);
            item.setCompletedAt(status == ItemStatus.DONE ? Instant.now() : null);
            progress.record(userId, ProgressRecorder.ROADMAP_ITEM_STATUS_CHANGED,
                    Map.of("itemId", item.getId(), "title", item.getTitle(), "from", from, "to", status));
        }
        return toDto(item.getRoadmap());
    }

    /** Top gaps by priority plus their unmet prerequisites, ordered so prerequisites come first. */
    static List<SkillGapItem> selectModules(List<SkillGapItem> gapItems) {
        List<SkillGapItem> open = gapItems.stream().filter(i -> i.gap() > 0).toList();
        Map<String, SkillGapItem> bySlug = new LinkedHashMap<>();
        open.forEach(i -> bySlug.put(i.skill().slug(), i));

        Set<String> chosen = new java.util.LinkedHashSet<>();
        open.stream().limit(MAX_MODULES).forEach(i -> chosen.add(i.skill().slug()));
        for (String slug : new ArrayList<>(chosen)) {
            addPrereqs(slug, bySlug, chosen);
        }
        List<String> limited = chosen.stream().limit(MAX_MODULES_WITH_PREREQS).toList();

        // Kahn's algorithm; among available modules take the highest priority first
        Map<String, Set<String>> deps = new HashMap<>();
        for (String s : limited) {
            deps.put(s, new HashSet<>(PREREQUISITES.getOrDefault(s, List.of()).stream()
                    .filter(limited::contains).toList()));
        }
        Comparator<String> byPriority = Comparator.comparingDouble((String s) -> bySlug.get(s).priority()).reversed()
                .thenComparing(Comparator.naturalOrder());
        PriorityQueue<String> ready = new PriorityQueue<>(byPriority);
        deps.forEach((s, d) -> {
            if (d.isEmpty()) {
                ready.add(s);
            }
        });
        List<SkillGapItem> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String s = ready.poll();
            ordered.add(bySlug.get(s));
            for (Map.Entry<String, Set<String>> e : deps.entrySet()) {
                if (e.getValue().remove(s) && e.getValue().isEmpty()) {
                    ready.add(e.getKey());
                }
            }
        }
        return ordered;
    }

    private static void addPrereqs(String slug, Map<String, SkillGapItem> open, Set<String> chosen) {
        for (String p : PREREQUISITES.getOrDefault(slug, List.of())) {
            if (open.containsKey(p) && chosen.add(p)) {
                addPrereqs(p, open, chosen);
            }
        }
    }

    static String rationale(SkillGapItem m, int jobsAnalyzed, String profession) {
        String demand = "MARKET".equals(m.importanceSource())
                ? "Встречается в " + Math.round(m.demandShare() * 100) + "% вакансий " + profession + " ("
                        + m.jobsMentioning() + " из " + jobsAnalyzed + ")."
                : "Базовое требование для роли " + profession + " (важность " + m.importance() + "/5).";
        return demand + " Требуемый уровень " + m.requiredLevel() + "/5, ваш — " + m.currentLevel() + "/5 ("
                + sourceLabel(m.currentSource()) + ").";
    }

    private static String sourceLabel(SkillSource s) {
        if (s == null) {
            return "нет в профиле";
        }
        return switch (s) {
            case SELF_REPORTED -> "самооценка";
            case CV_EXTRACTED -> "по данным CV";
            case ASSESSED -> "подтверждён заданием";
            case VERIFIED -> "подтверждён внешним источником";
        };
    }

    public RoadmapDto toDto(Roadmap r) {
        List<RoadmapDto.Item> items = r.getItems().stream().map(i -> new RoadmapDto.Item(
                i.getId(), i.getPosition(), i.getSkill() == null ? null : SkillDto.from(i.getSkill()), i.getTitle(),
                i.getGoal(), i.getTopics() == null || i.getTopics().isBlank() ? List.of()
                        : Arrays.stream(i.getTopics().split(";")).map(String::trim).toList(),
                i.getEstimatedHours(), i.getCompletionCriteria(), i.getRationale(), i.getStatus(), i.getCompletedAt(),
                i.getResources().stream().map(LearningResourceDto::from).toList())).toList();
        int done = (int) items.stream().filter(i -> i.status() == ItemStatus.DONE).count();
        int inProgress = (int) items.stream().filter(i -> i.status() == ItemStatus.IN_PROGRESS).count();
        double percent = items.isEmpty() ? 0 : Math.round(done * 1000.0 / items.size()) / 10.0;
        var p = r.getCareerGoal().getProfession();
        return new RoadmapDto(r.getId(), new CareerGoalDto.ProfessionRef(p.getId(), p.getSlug(), p.getTitle()),
                r.getStatus(), r.getCreatedAt(), new RoadmapDto.Progress(items.size(), done, inProgress, percent),
                NOTE, items);
    }
}
