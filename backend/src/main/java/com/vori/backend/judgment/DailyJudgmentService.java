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
    private record Evaluation(Signal signal, int count, Map<StatType,Integer> rewards, Map<StatType, DailyJudgmentResponse.GroupJudgment> groups, int saved) {}

    @Transactional(readOnly=true) public Optional<DailyJudgmentResponse> getToday(Long id){ return getByDate(id, LocalDate.now()); }
    @Transactional(readOnly=true) public Optional<DailyJudgmentResponse> getByDate(Long id,LocalDate date){return judgments.findByUserIdAndJudgmentDate(id,date).map(j->DailyJudgmentResponse.from(j,true));}
    @Transactional(readOnly=true) public List<DailyJudgmentResponse> getByMonth(Long id,YearMonth month){return judgments.findByUserIdAndJudgmentDateBetweenOrderByJudgmentDate(id,month.atDay(1),month.plusMonths(1).atDay(1)).stream().map(j->DailyJudgmentResponse.from(j,true)).toList();}
    @Transactional public DailyJudgmentResponse judgeToday(Long id,Role role){return judgeDate(id,role,LocalDate.now());}

    @Transactional public DailyJudgmentResponse judgeDate(Long id,Role role,LocalDate date){
        LocalDateTime now=LocalDateTime.now();
        if(role!=Role.ADMIN&&!date.equals(now.toLocalDate())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"다른 날짜의 판정은 관리자만 사용할 수 있어요.");
        if(role!=Role.ADMIN&&now.getHour()<openHour) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"소비 판정은 매일 "+openHour+"시부터 자정까지 가능해요.");
        User user=users.findByIdForUpdate(id).orElseThrow();
        Optional<DailyJudgment> old=judgments.findByUserIdAndJudgmentDate(id,date);
        if(old.isPresent() && role!=Role.ADMIN) return DailyJudgmentResponse.from(old.get(),true);
        Evaluation evaluation=evaluate(id,date); grant(user,evaluation,now);
        String details=evaluation.rewards.entrySet().stream().filter(e->e.getValue()>0).map(e->e.getKey()+":"+e.getValue()).collect(Collectors.joining(","));
        // 스탯별 결과와 실제 절약액도 저장해, 판정을 다시 열었을 때 판정한 순간과 같은 화면을 보여 준다.
        String groupDetails=DailyJudgmentResponse.groupsToJson(evaluation.groups);
        if(old.isPresent()) {
            DailyJudgment previous=old.get();
            previous.refresh(evaluation.signal,evaluation.count,evaluation.saved/100,evaluation.rewards.values().stream().mapToInt(Integer::intValue).max().orElse(0),details,groupDetails,evaluation.saved,now);
            return new DailyJudgmentResponse(date,evaluation.signal,evaluation.count,evaluation.saved/100,previous.getStatRewardPerType(),evaluation.rewards,evaluation.groups,evaluation.saved,now,false);
        }
        DailyJudgment saved=judgments.save(DailyJudgment.builder().userId(id).judgmentDate(date).signal(evaluation.signal).expenseCount(evaluation.count).coinReward(evaluation.saved/100).statRewardPerType(evaluation.rewards.values().stream().mapToInt(Integer::intValue).max().orElse(0)).rewardDetails(details).groupDetails(groupDetails).savedAmount(evaluation.saved).judgedAt(now).build());
        return new DailyJudgmentResponse(date,evaluation.signal,evaluation.count,evaluation.saved/100,saved.getStatRewardPerType(),evaluation.rewards,evaluation.groups,evaluation.saved,now,false);
    }

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
            // 그룹의 과소비 판정도 같은 수준까지 완화한다.
            Signal approved=todayRows.stream().filter(e -> e.getStatType()==type)
                    .map(Expense::getSignalFinal).filter(s -> s != Signal.RED)
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
