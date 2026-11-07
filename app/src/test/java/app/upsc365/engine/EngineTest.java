package app.upsc365.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class EngineTest {

    private static List<Integer> flex() {
        List<Integer> f = new ArrayList<>();
        for (int i = 1; i <= 13; i++) f.add(23 * i);
        return f;
    }

    private static List<Engine.CalDay> calendar() {
        return Engine.buildCalendar(LocalDate.of(2026, 12, 1), flex(), 300);
    }

    private static int count(List<Engine.CalDay> cal, Engine.DayType t) {
        int n = 0;
        for (Engine.CalDay c : cal) if (c.type == t) n++;
        return n;
    }

    private static List<Engine.Item> items(int n, Engine.Tier gsTier) {
        List<Engine.Item> list = new ArrayList<>();
        for (int d = 1; d <= n; d++) {
            list.add(new Engine.Item(d, Engine.Stream.MATH, Engine.Tier.CORE,
                    d > 1 ? Arrays.asList(d - 1) : Collections.<Integer>emptyList()));
            list.add(new Engine.Item(d, Engine.Stream.GS, gsTier,
                    d > 1 ? Arrays.asList(d - 1) : Collections.<Integer>emptyList()));
        }
        return list;
    }

    private static Map<Integer, LocalDate> dates(List<Engine.CalDay> cal) {
        return Engine.spineDates(cal);
    }

    @Test public void calendarHasExpectedShape() {
        List<Engine.CalDay> cal = calendar();
        assertEquals(365, cal.size());
        assertEquals(52, count(cal, Engine.DayType.SUNDAY));
        assertEquals(13, count(cal, Engine.DayType.FLEX));
        assertEquals(300, count(cal, Engine.DayType.SPINE));
        assertEquals(LocalDate.of(2026, 12, 1), cal.get(0).date);
        Engine.CalDay last = cal.get(cal.size() - 1);
        assertEquals(LocalDate.of(2027, 11, 30), last.date);
        assertEquals(Engine.DayType.SPINE, last.type);
        assertEquals(300, last.spineDay);
    }

    @Test public void calendarNeverPlacesSpineOnSunday() {
        for (Engine.CalDay c : calendar()) {
            if (c.type == Engine.DayType.SPINE) assertFalse(c.date.getDayOfWeek() == DayOfWeek.SUNDAY);
            if (c.type == Engine.DayType.SUNDAY) assertEquals(DayOfWeek.SUNDAY, c.date.getDayOfWeek());
        }
    }

    @Test public void calendarCompletesForAnyStartWeekday() {
        for (int shift = 0; shift < 7; shift++) {
            List<Engine.CalDay> cal = Engine.buildCalendar(LocalDate.of(2026, 12, 1).plusDays(shift), flex(), 300);
            assertEquals(300, count(cal, Engine.DayType.SPINE));
            assertEquals(13, count(cal, Engine.DayType.FLEX));
            assertTrue(cal.size() >= 365);
        }
    }

    @Test public void spineDaysAreSequential() {
        int expected = 1;
        for (Engine.CalDay c : calendar()) if (c.type == Engine.DayType.SPINE) assertEquals(expected++, c.spineDay);
    }

    @Test public void nothingOverdueBeforeDueDate() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.QueueEntry> q = Engine.recoveryQueue(items(10, Engine.Tier.CORE), dates(cal),
                new HashMap<String, Engine.Status>(), LocalDate.of(2026, 12, 1), cal);
        assertTrue(q.isEmpty());
    }

    @Test public void mathComesFirstAndDependenciesAreRespected() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = items(6, Engine.Tier.CORE);
        List<Engine.QueueEntry> q = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 8), cal);
        // Dec 8, 2026 is a Tuesday: spine days 1..6 (Dec 1-5 and Mon Dec 7) are overdue, two items each
        assertEquals(12, q.size());
        boolean seenGs = false;
        int lastMath = 0, lastGs = 0;
        for (Engine.QueueEntry e : q) {
            if (e.item.stream == Engine.Stream.GS) seenGs = true;
            else assertFalse("math after gs", seenGs);
            if (e.item.stream == Engine.Stream.MATH) { assertTrue(e.item.day > lastMath); lastMath = e.item.day; }
            else { assertTrue(e.item.day > lastGs); lastGs = e.item.day; }
        }
    }

    @Test public void prerequisiteBeatsTierOrdering() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = new ArrayList<>();
        its.add(new Engine.Item(1, Engine.Stream.GS, Engine.Tier.STANDARD, Collections.<Integer>emptyList()));
        its.add(new Engine.Item(2, Engine.Stream.GS, Engine.Tier.CORE, Arrays.asList(1)));
        List<Engine.QueueEntry> q = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 20), cal);
        assertEquals(2, q.size());
        assertEquals(1, q.get(0).item.day);
        assertEquals(2, q.get(1).item.day);
    }

    @Test public void coreOutranksExtendedWhenIndependent() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = new ArrayList<>();
        its.add(new Engine.Item(1, Engine.Stream.GS, Engine.Tier.EXTENDED, Collections.<Integer>emptyList()));
        its.add(new Engine.Item(2, Engine.Stream.GS, Engine.Tier.CORE, Collections.<Integer>emptyList()));
        List<Engine.QueueEntry> q = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 20), cal);
        assertEquals(2, q.get(0).item.day);
    }

    @Test public void doneItemsLeaveQueue() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = items(3, Engine.Tier.CORE);
        Map<String, Engine.Status> st = new HashMap<>();
        st.put("d1:M", Engine.Status.DONE);
        st.put("d1:G", Engine.Status.SKIMMED);
        List<Engine.QueueEntry> q = Engine.recoveryQueue(its, dates(cal), st, LocalDate.of(2026, 12, 3), cal);
        for (Engine.QueueEntry e : q) assertTrue(e.item.day >= 2 || false);
        assertEquals(2, q.size()); // d2 M and G are overdue on Dec 3 (day 3 date)
    }

    @Test public void extendedItemsDowngradeToSkimAfterTwoSundays() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = new ArrayList<>();
        its.add(new Engine.Item(1, Engine.Stream.GS, Engine.Tier.EXTENDED, Collections.<Integer>emptyList()));
        its.add(new Engine.Item(1, Engine.Stream.MATH, Engine.Tier.CORE, Collections.<Integer>emptyList()));
        // Day 1 is 2026-12-01. Sundays: Dec 6, Dec 13. On Dec 14 two Sundays have passed.
        List<Engine.QueueEntry> early = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 13), cal);
        for (Engine.QueueEntry e : early) assertFalse(e.skim);
        List<Engine.QueueEntry> later = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 14), cal);
        for (Engine.QueueEntry e : later) {
            if (e.item.stream == Engine.Stream.GS) {
                assertTrue(e.skim);
                assertEquals(Engine.SKIM_HOURS, e.cost, 1e-9);
            } else {
                assertFalse(e.skim); // core Mathematics never downgrades
                assertEquals(Engine.MATH_HOURS, e.cost, 1e-9);
            }
        }
    }

    @Test public void packRespectsBudgetAndOrder() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = items(8, Engine.Tier.CORE);
        List<Engine.QueueEntry> q = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 20), cal);
        Engine.Pack p = Engine.pack(q, Engine.SUNDAY_CAP);
        assertTrue(p.hoursUsed <= Engine.SUNDAY_CAP + 1e-9);
        assertEquals(q.size(), p.picked.size() + p.leftover.size());
        // 11 h budget: 4 math sessions (10 h) fit before any 4.75 h GS; the first GS item no longer fits.
        assertEquals(4, p.picked.size());
        for (Engine.QueueEntry e : p.picked) assertEquals(Engine.Stream.MATH, e.item.stream);
    }

    @Test public void packNeverPicksDependentOfSkippedPrerequisite() {
        List<Engine.CalDay> cal = calendar();
        List<Engine.Item> its = new ArrayList<>();
        its.add(new Engine.Item(1, Engine.Stream.GS, Engine.Tier.CORE, Collections.<Integer>emptyList()));
        its.add(new Engine.Item(2, Engine.Stream.GS, Engine.Tier.CORE, Arrays.asList(1)));
        its.add(new Engine.Item(3, Engine.Stream.GS, Engine.Tier.CORE, Collections.<Integer>emptyList()));
        List<Engine.QueueEntry> q = Engine.recoveryQueue(its, dates(cal), new HashMap<String, Engine.Status>(),
                LocalDate.of(2026, 12, 20), cal);
        Engine.Pack p = Engine.pack(q, 5.0); // only one GS item fits (4.75 h)
        assertEquals(1, p.picked.size());
        assertEquals(1, p.picked.get(0).item.day);
        // item 2 depends on 1 (picked), item 3 is independent but does not fit
        assertEquals(2, p.leftover.size());
    }

    @Test public void backlogLevelsAndProjection() {
        assertEquals(Engine.Level.OK, Engine.level(11.9));
        assertEquals(Engine.Level.YELLOW, Engine.level(12.0));
        assertEquals(Engine.Level.YELLOW, Engine.level(29.9));
        assertEquals(Engine.Level.RED, Engine.level(30.0));
        List<Engine.CalDay> cal = calendar();
        assertEquals(0, Engine.projectedDelayDays(0, LocalDate.of(2026, 12, 1), cal));
        assertEquals(0, Engine.projectedDelayDays(100, LocalDate.of(2026, 12, 1), cal));
        // capacity before the final day is 52*11 + 13*8 = 676 h; 8 h beyond that is a one-day slip
        assertEquals(1, Engine.projectedDelayDays(684, LocalDate.of(2026, 12, 1), cal));
        // late in the programme capacity shrinks
        assertTrue(Engine.projectedDelayDays(100, LocalDate.of(2027, 11, 20), cal) > 0);
    }

    @Test public void blockedByNearestPendingPrerequisite() {
        List<Engine.Item> its = items(4, Engine.Tier.CORE);
        Map<String, Engine.Item> by = Engine.index(its);
        Map<String, Engine.Status> st = new HashMap<>();
        Engine.Item d3g = by.get("d3:G");
        assertNotNull(Engine.firstPendingPrereq(d3g, by, st));
        assertEquals(2, Engine.firstPendingPrereq(d3g, by, st).day);
        st.put("d2:G", Engine.Status.DONE);
        assertNull(Engine.firstPendingPrereq(d3g, by, st));
    }

    @Test public void reviewsFallDueAtOneSevenThirty() {
        Map<String, LocalDate> done = new HashMap<>();
        LocalDate d0 = LocalDate.of(2026, 12, 1);
        done.put("d1:G", d0);
        Set<String> completed = new HashSet<>();
        assertEquals(0, Engine.dueReviews(done, completed, d0).shown.size());
        assertEquals(1, Engine.dueReviews(done, completed, d0.plusDays(1)).shown.size());
        assertEquals(1, Engine.dueReviews(done, completed, d0.plusDays(2)).shown.size());
        completed.add("d1:G#1");
        assertEquals(0, Engine.dueReviews(done, completed, d0.plusDays(6)).shown.size());
        Engine.ReviewResult r7 = Engine.dueReviews(done, completed, d0.plusDays(7));
        assertEquals(1, r7.shown.size());
        assertEquals(7, r7.shown.get(0).interval);
        completed.add("d1:G#7");
        Engine.ReviewResult r30 = Engine.dueReviews(done, completed, d0.plusDays(30));
        assertEquals(1, r30.shown.size());
        assertEquals(30, r30.shown.get(0).interval);
    }

    @Test public void reviewsLapseAndAreCapped() {
        Map<String, LocalDate> done = new HashMap<>();
        LocalDate d0 = LocalDate.of(2026, 12, 1);
        for (int i = 1; i <= 12; i++) done.put("d" + i + ":G", d0);
        Engine.ReviewResult r = Engine.dueReviews(done, new HashSet<String>(), d0.plusDays(1));
        assertEquals(Engine.REVIEW_CAP, r.shown.size());
        assertEquals(12 - Engine.REVIEW_CAP, r.extra);
        // 15 days later the +1 reviews (overdue 14 days) have lapsed; the +7 reviews (overdue 8) are still shown
        Engine.ReviewResult later = Engine.dueReviews(done, new HashSet<String>(), d0.plusDays(15));
        assertEquals(12, later.lapsed);
        assertEquals(Engine.REVIEW_CAP, later.shown.size());
        for (Engine.Review rv : later.shown) assertEquals(7, rv.interval);
    }
}
