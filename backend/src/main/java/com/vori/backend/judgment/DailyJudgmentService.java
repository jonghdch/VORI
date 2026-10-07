package com.vori.backend.judgment;

import com.vori.backend.budget.*;
import com.vori.backend.common.StatType;
import com.vori.backend.expense.*;
import com.vori.backend.furniture.*;
import com.vori.backend.pet.*;
import com.vori.backend.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class DailyJudgmentService {
    @Value("${vori.ai-judge.open-hour:20}") private int openHour;
    private final DailyJudgmentRepository judgments; private final ExpenseRepository expenses; private final UserRepository users;
    private final PetRepository pets; private final PetGrowthLogRepository logs; private final UserFurnitureRepository furniture;
    private final SpendingPlanService plans; private final UserStatBudgetRepository budgets; private final PetStatRewardService statRewardService;
    // 펫 성장 알림·펫 칭호 확인 — main(#89 알림·펫 칭호)과 같은 연결
    private final com.vori.backend.notification.NotificationService notificationService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    // 예외 지출(AI 질문) — 1차 판정에서 기다릴 사유가 있는지, 확정 때 어느 지출이 인정됐는지 본다
    private final com.vori.backend.inquiry.AiInquiryRepository inquiries;
    private record Evaluation(Signal signal, int count, Map<StatType,Integer> rewards, Map<StatType, DailyJudgmentResponse.GroupJudgment> groups, int saved) {}

    @Transactional(readOnly=true) public Optional<DailyJudgmentResponse> getToday(Long id){ return getByDate(id, LocalDate.now()); }
    @Transactional(readOnly=true) public Optional<DailyJudgmentResponse> getByDate(Long id,LocalDate date){return judgments.findByUserIdAndJudgmentDate(id,date).map(j->DailyJudgmentResponse.from(j,true));}
    @Transactional(readOnly=true) public List<DailyJudgmentResponse> getByMonth(Long id,YearMonth month){return judgments.findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(id,month.atDay(1),month.plusMonths(1).atDay(1)).stream().map(j->DailyJudgmentResponse.from(j,true)).toList();}
    @Transactional public DailyJudgmentResponse judgeToday(Long id,Role role){return judgeDate(id,role,LocalDate.now());}

    /**
     * 1차 판정 (docs/judgment-flow.md ①). 봉투로 계산해 PENDING 으로 저장하고 보상은 아직 주지 않는다.
     * 그날 답할 예외 지출(답변 안 된 AI 질문)이 없으면 같은 요청에서 바로 확정·지급한다.
     * 판정 행이 이미 있으면 다시 계산하지 않는다 — PENDING 은 1차 결과(이어서 사유 입력), FINALIZED 는 최종 결과.
     * 관리자는 확정된 날도 다시 계산해 갱신하지만 보상은 다시 주지 않는다(시연·검증용, 보상 중복 방지).
     */
    @Transactional public DailyJudgmentResponse judgeDate(Long id,Role role,LocalDate date){
        LocalDateTime now=LocalDateTime.now();
        if(role!=Role.ADMIN&&!date.equals(now.toLocalDate())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"다른 날짜의 판정은 관리자만 사용할 수 있어요.");
        if(role!=Role.ADMIN&&now.getHour()<openHour) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"소비 판정은 매일 "+openHour+"시부터 자정까지 가능해요.");
        User user=users.findByIdForUpdate(id).orElseThrow();
        Optional<DailyJudgment> old=judgments.findByUserIdAndJudgmentDate(id,date);
        if(old.isPresent()) {
            DailyJudgment existing=old.get();
            if(role==Role.ADMIN && existing.isFinalized()) {
                Evaluation evaluation=evaluate(id,date);
                existing.refreshResult(evaluation.signal,evaluation.count,DailyJudgmentResponse.groupsToJson(evaluation.groups),now);
                return DailyJudgmentResponse.from(existing,false);
            }
            return DailyJudgmentResponse.from(existing,existing.isFinalized());
        }
        Evaluation first=evaluate(id,date);
        String groupDetails=DailyJudgmentResponse.groupsToJson(first.groups);
        // 확정 전까지 최종 칸에도 1차 결과를 넣어 둔다(확정 때 덮어쓴다). 보상 칸은 "확정 때 줄 예정" 값이다.
        DailyJudgment saved=judgments.save(DailyJudgment.builder().userId(id).judgmentDate(date).signal(first.signal).expenseCount(first.count).coinReward(first.saved/100).statRewardPerType(maxReward(first)).rewardDetails(details(first)).groupDetails(groupDetails).savedAmount(first.saved).judgedAt(now)
                .status(JudgmentStatus.PENDING).initialSignal(first.signal).initialGroupDetails(groupDetails).build());
        if(pendingInquiryCount(id,date)==0) return finalizeRow(user,saved,now);
        return DailyJudgmentResponse.from(saved,false);
    }

    /**
     * 확정 (docs/judgment-flow.md ③). 예외 지출 사유 입력이 끝났거나 건너뛰었을 때 화면이 부른다.
     * 답변으로 인정된 지출을 반영해 그날을 다시 계산하고 보상을 한 번 지급한다. 답하지 않은 지출은 빨강 그대로다.
     * 판정은 그날 안에만 확정할 수 있다(관리자 제외). 이미 확정된 날은 저장된 결과를 그대로 돌려준다 — 연속 클릭·재시도에도 보상은 한 번.
     */
    @Transactional public DailyJudgmentResponse finalizeDate(Long id,Role role,LocalDate date){
        LocalDateTime now=LocalDateTime.now();
        if(role!=Role.ADMIN&&!date.equals(now.toLocalDate())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"판정은 그날 안에 확정할 수 있어요.");
        User user=users.findByIdForUpdate(id).orElseThrow();
        DailyJudgment row=judgments.findByUserIdAndJudgmentDate(id,date)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"먼저 판정을 받아 주세요."));
        if(row.isFinalized()) return DailyJudgmentResponse.from(row,true);
        return finalizeRow(user,row,now);
    }

    /**
     * 자정이 지난 미확정 판정을 1차 판정 내용으로 확정하고 그 보상을 지급한다(docs/judgment-flow.md D7).
     * 예외 지출 사유는 반영하지 않는다 — 다시 계산하지 않고 1차 판정 때 저장한 결과·보상을 그대로 쓴다.
     * 사용자 행을 잠근 뒤 다시 읽어 PENDING 일 때만 진행하므로, 사용자의 확정 요청과 겹쳐도 보상은 한 번이다.
     * @return 이번 호출로 확정했으면 true
     */
    @Transactional public boolean finalizeExpiredAsInitial(Long judgmentId){
        DailyJudgment found=judgments.findById(judgmentId).orElse(null);
        if(found==null) return false;
        User user=users.findByIdForUpdate(found.getUserId()).orElseThrow();
        DailyJudgment row=judgments.findByUserIdAndJudgmentDate(user.getId(),found.getJudgmentDate()).orElse(null);
        if(row==null || row.isFinalized()) return false;
        LocalDateTime now=LocalDateTime.now();
        row.finalizeAsInitial(now);
        DailyJudgmentResponse stored=DailyJudgmentResponse.from(row,false);
        grant(user,new Evaluation(row.getSignal(),row.getExpenseCount(),stored.statRewards(),stored.groupJudgments(),stored.savedAmount()),now);
        return true;
    }

    private DailyJudgmentResponse finalizeRow(User user,DailyJudgment row,LocalDateTime now){
        Evaluation evaluation=evaluate(user.getId(),row.getJudgmentDate());
        row.finalizeWith(evaluation.signal,evaluation.count,evaluation.saved/100,maxReward(evaluation),details(evaluation),DailyJudgmentResponse.groupsToJson(evaluation.groups),evaluation.saved,now);
        grant(user,evaluation,now);
        return DailyJudgmentResponse.from(row,false);
    }

    /** 그날 아직 답하지 않은 AI 질문 수 — 0 이면 사유를 기다릴 것이 없어 1차 판정에서 바로 확정한다. */
    private int pendingInquiryCount(Long userId,LocalDate date){
        return inquiries.findPendingByDate(userId,date.atStartOfDay(),date.plusDays(1).atStartOfDay()).size();
    }
    private static int maxReward(Evaluation e){return e.rewards.values().stream().mapToInt(Integer::intValue).max().orElse(0);}
    private static String details(Evaluation e){return e.rewards.entrySet().stream().filter(x->x.getValue()>0).map(x->x.getKey()+":"+x.getValue()).collect(Collectors.joining(","));}

    /** 같은 날 같은 스탯 그룹의 모든 지출을 합쳐, 월 예산의 누적 일일 한도와 비교한다. */
    private Evaluation evaluate(Long userId,LocalDate date){
        YearMonth month=YearMonth.from(date); String ym=month.toString(); plans.ensurePlan(userId,ym);
        Map<StatType,Integer> budget=new EnumMap<>(StatType.class); budgets.findByUserIdAndYearMonth(userId,ym).forEach(b->budget.put(b.getStatType(),b.getAmount()));
        List<Expense> todayRows=expenses.findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(userId,date.atStartOfDay(),date.plusDays(1).atStartOfDay());
        Map<StatType,Integer> used=sum(expenses.findByUserIdAndSpentAtBetween(userId,month.atDay(1).atStartOfDay(),date.plusDays(1).atStartOfDay()));
        Map<LocalDate,Map<StatType,Integer>> spendingByDay=new HashMap<>();
        expenses.findByUserIdAndSpentAtBetween(userId,month.atDay(1).atStartOfDay(),date.plusDays(1).atStartOfDay()).forEach(expense ->
                spendingByDay.computeIfAbsent(expense.getSpentAt().toLocalDate(), ignored -> new EnumMap<>(StatType.class))
                        .merge(expense.getStatType(),expense.getAmount(),Integer::sum));
        Map<StatType,Integer> rewards=new EnumMap<>(StatType.class);
        Map<StatType,DailyJudgmentResponse.GroupJudgment> groups=new EnumMap<>(StatType.class);
        Set<Long> approvedIds=approvedExceptionIds(todayRows);
        int saved=0; Signal overall=Signal.GREEN;
        for(StatType type:StatType.values()) {
            int monthlyBudget=budget.getOrDefault(type,0);
            int daysInMonth=month.lengthOfMonth();
            int dailyBase=monthlyBudget/daysInMonth;
            // 일일 봉투: 전날 남긴 금액은 절반만 다음 날에 더하고, 초과 금액은 전부 차감한다.
            // 누적 나눗셈 차이를 기본 봉투로 써 월 예산의 나머지 1원도 정확히 배분한다.
            int carry=0;
            int availableToday=0;
            int todaySpent=0;
            for(int day=1; day<=date.getDayOfMonth(); day++) {
                int baseForDay=monthlyBudget*day/daysInMonth-monthlyBudget*(day-1)/daysInMonth;
                int available=Math.max(0,baseForDay+carry);
                int spent=spendingByDay.getOrDefault(month.atDay(day),Map.of()).getOrDefault(type,0);
                if(day==date.getDayOfMonth()) { availableToday=available; todaySpent=spent; break; }
                int balance=available-spent;
                carry=balance>0 ? balance/2 : balance;
            }
            int monthSpent=used.getOrDefault(type,0);
            Signal signal=todaySpent<=Math.floor(availableToday*.85)?Signal.GREEN:todaySpent<=availableToday?Signal.GRAY:Signal.RED;
            // 이미 월 예산을 소진해 오늘 쓸 수 있는 돈이 0원이면, 지출을 쉬었더라도
            // 절약 상태가 아니다. 보상 없이 주황(예산 소진) 상태로 안내한다.
            boolean budgetExhausted=monthlyBudget>0 && monthSpent>=monthlyBudget && availableToday==0 && todaySpent==0;
            if(budgetExhausted) signal=Signal.GRAY;
            // AI가 답변 사유를 합당한 예외 지출로 인정한 경우, 그 지출이 속한
            // 그룹의 과소비 판정도 같은 수준까지 완화한다. 처음부터 빨강이 아니던 평범한 지출은
            // 완화 근거가 아니다 — 예전엔 그런 지출 하나만 있어도 예산을 넘긴 그룹이 풀렸다.
            Signal approved=todayRows.stream().filter(e -> e.getStatType()==type && e.getId()!=null && approvedIds.contains(e.getId()))
                    .map(Expense::getSignalFinal).filter(s -> s != null && s != Signal.RED)
                    .min(Comparator.comparingInt(this::rank)).orElse(Signal.RED);
            if(signal==Signal.RED && approved!=Signal.RED) signal=approved;
            int groupSaved=Math.max(0,availableToday-todaySpent);
            // 하루 동안 남긴 예산 1,000원마다 스탯 1을 준다. 너무 큰 월 예산으로
            // 하루 성장량이 과도해지지 않도록 그룹당 최대 15로 제한한다.
            int statReward=Math.min(15,groupSaved/1000);
            rewards.put(type,statReward);
            groups.put(type,new DailyJudgmentResponse.GroupJudgment(monthlyBudget,dailyBase,availableToday,todaySpent,monthSpent,groupSaved,groupSaved/2,budgetExhausted,signal));
            saved+=groupSaved; overall=stronger(overall,signal);
        }
        return new Evaluation(overall,todayRows.size(),rewards,groups,saved);
    }
    /** 처음엔 빨강이었고 AI 질문 답변으로 신호가 바뀐(signal_adjusted) 지출 — 그룹 판정을 완화할 수 있는 예외 지출. */
    private Set<Long> approvedExceptionIds(List<Expense> rows){
        List<Long> redIds=rows.stream().filter(e->e.getSignalInitial()==Signal.RED && e.getId()!=null).map(Expense::getId).toList();
        if(redIds.isEmpty()) return Set.of();
        return inquiries.findByExpenseIdIn(redIds).stream().filter(i->Boolean.TRUE.equals(i.getSignalAdjusted()))
                .map(com.vori.backend.inquiry.AiInquiry::getExpenseId).collect(Collectors.toSet());
    }
    private Map<StatType,Integer> sum(List<Expense> rows){Map<StatType,Integer> sums=new EnumMap<>(StatType.class);rows.forEach(e->sums.merge(e.getStatType(),e.getAmount(),Integer::sum));return sums;}
    private void grant(User user,Evaluation result,LocalDateTime now){
        user.addGameMoney(result.saved/100); List<Pet> active=pets.findByUserIdAndReleasedAtIsNull(user.getId());if(active.isEmpty())return; Pet pet=active.get(0);
        int levelBefore = pet.level();
        Map<StatType,BigDecimal> pct=new EnumMap<>(StatType.class);for(UserFurniture f:furniture.findByUserIdAndPositionXIsNotNullAndPositionYIsNotNull(user.getId()))pct.merge(f.getStatTarget(),Optional.ofNullable(f.getReleaseBonusPct()).orElse(BigDecimal.ZERO),BigDecimal::add);
        for(var entry:result.rewards.entrySet()) { StatType type=entry.getKey(); int base=entry.getValue(); int applied=statRewardService.grant(pet,user.getId(),type,base); if(applied>0)log(pet,user,type,applied,GrowthReason.DAILY_JUDGMENT,now); int bonus=pct.getOrDefault(type,BigDecimal.ZERO).signum()==0?0:BigDecimal.valueOf(base).multiply(pct.get(type)).movePointLeft(2).setScale(0,RoundingMode.CEILING).intValue(); int appliedBonus=statRewardService.grant(pet,user.getId(),type,bonus); if(appliedBonus>0)log(pet,user,type,appliedBonus,GrowthReason.FURNITURE_BONUS,now); }
        pet.evaluateStage();
        notificationService.petGrew(user.getId(), pet, levelBefore);
        eventPublisher.publishEvent(new com.vori.backend.pettitle.PetTitleCheckEvent(user.getId(), "DAILY_JUDGMENT"));
    }
    private void log(Pet p,User u,StatType t,int d,GrowthReason reason,LocalDateTime at){logs.save(PetGrowthLog.builder().petId(p.getId()).userId(u.getId()).statType(t).delta(d).savedAmount(0).reason(reason).createdAt(at).build());}
    private Signal stronger(Signal a,Signal b){return rank(a)>=rank(b)?a:b;} private int rank(Signal s){return s==Signal.RED?3:s==Signal.GRAY?2:1;}
}
