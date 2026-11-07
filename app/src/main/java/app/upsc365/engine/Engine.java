package app.upsc365.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java scheduling engine for UPSC 365. No Android dependencies, so it is unit-testable on the JVM.
 *
 * Model: the dated "spine" (300 study days) is fixed. Every spine day owns two trackable items
 * (MATH and GS). An item still PENDING after its due date is "overdue" and enters the recovery queue.
 * Recovery happens on Sundays (capacity 11 h) and flex days (capacity 8 h). Dependencies are hard:
 * an item is never scheduled before its pending prerequisites.
 */
public final class Engine {
    private Engine() {}

    public enum Stream { MATH, GS }

    public enum Tier { CORE, STANDARD, EXTENDED }

    public enum DayType { SPINE, FLEX, SUNDAY }

    public enum Level { OK, YELLOW, RED }

    public enum Status {
        PENDING(0), DONE(1), SKIMMED(2);

        public final int code;

        Status(int code) { this.code = code; }

        public static Status of(int code) {
            for (Status s : values()) if (s.code == code) return s;
            return PENDING;
        }
    }

    public static final double MATH_HOURS = 2.5;
    public static final double GS_HOURS = 4.75;     // GS-A (2.5) + GS-B (2.25)
    public static final double SKIM_HOURS = 1.5;
    public static final double SUNDAY_CAP = 11.0;
    public static final double FLEX_CAP = 8.0;
    public static final double YELLOW_HOURS = 12.0;
    public static final double RED_HOURS = 30.0;
    public static final int SKIM_AFTER_SUNDAYS = 2;
    public static final int[] REVIEW_INTERVALS = {1, 7, 30};
    public static final int REVIEW_CAP = 9;          // about 45 minutes at ~5 minutes per review
    public static final int REVIEW_LAPSE_DAYS = 10;  // reviews overdue by more than this expire

    // ---------------------------------------------------------------- calendar

    public static final class CalDay {
        public final LocalDate date;
        public final DayType type;
        public final int spineDay; // 0 when not a spine day

        public CalDay(LocalDate date, DayType type, int spineDay) {
            this.date = date;
            this.type = type;
            this.spineDay = spineDay;
        }
    }

    /**
     * Builds the calendar from the start date: Sundays are recovery/consolidation days, a flex day
     * is inserted after each spine-day count listed in flexAfter, every other day is a spine day.
     * Runs until spineCount spine days are placed.
     */
    public static List<CalDay> buildCalendar(LocalDate start, List<Integer> flexAfter, int spineCount) {
        List<CalDay> out = new ArrayList<>();
        int spine = 0;
        int flexIdx = 0;
        LocalDate d = start;
        while (spine < spineCount) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                out.add(new CalDay(d, DayType.SUNDAY, 0));
            } else if (flexIdx < flexAfter.size() && spine == flexAfter.get(flexIdx)) {
                out.add(new CalDay(d, DayType.FLEX, 0));
                flexIdx++;
            } else {
                spine++;
                out.add(new CalDay(d, DayType.SPINE, spine));
            }
            d = d.plusDays(1);
        }
        return out;
    }

    public static CalDay find(List<CalDay> cal, LocalDate date) {
        for (CalDay c : cal) if (c.date.equals(date)) return c;
        return null;
    }

    public static Map<Integer, LocalDate> spineDates(List<CalDay> cal) {
        Map<Integer, LocalDate> m = new HashMap<>();
        for (CalDay c : cal) if (c.type == DayType.SPINE) m.put(c.spineDay, c.date);
        return m;
    }

    // ---------------------------------------------------------------- items

    public static final class Item {
        public final int day;
        public final Stream stream;
        public final Tier tier;
        public final List<Integer> prereqs; // earlier day numbers in the same stream

        public Item(int day, Stream stream, Tier tier, List<Integer> prereqs) {
            this.day = day;
            this.stream = stream;
            this.tier = tier;
            this.prereqs = prereqs;
        }

        public String key() { return key(day, stream); }

        public static String key(int day, Stream stream) {
            return "d" + day + ":" + (stream == Stream.MATH ? "M" : "G");
        }

        public double hours() { return stream == Stream.MATH ? MATH_HOURS : GS_HOURS; }
    }

    public static Map<String, Item> index(List<Item> items) {
        Map<String, Item> m = new HashMap<>();
        for (Item it : items) m.put(it.key(), it);
        return m;
    }

    private static Status statusOf(Map<String, Status> status, String key) {
        Status s = status.get(key);
        return s == null ? Status.PENDING : s;
    }

    /** Nearest unmet prerequisite of an item (lowest day number), or null if none. */
    public static Item firstPendingPrereq(Item item, Map<String, Item> byKey, Map<String, Status> status) {
        Item best = null;
        for (int p : item.prereqs) {
            String k = Item.key(p, item.stream);
            if (statusOf(status, k) == Status.PENDING) {
                Item cand = byKey.get(k);
                if (cand != null && (best == null || cand.day < best.day)) best = cand;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- recovery queue

    public static final class QueueEntry {
        public final Item item;
        public final LocalDate due;
        public final int ageDays;
        public final int sundaysCarried;
        public final boolean skim;
        public final double cost;

        QueueEntry(Item item, LocalDate due, int ageDays, int sundaysCarried, boolean skim, double cost) {
            this.item = item;
            this.due = due;
            this.ageDays = ageDays;
            this.sundaysCarried = sundaysCarried;
            this.skim = skim;
            this.cost = cost;
        }
    }

    /**
     * Overdue = PENDING and due before today. Order: Mathematics first, then tier (core, standard,
     * extended), then day number; any pending prerequisite is always emitted before its dependent.
     * Extended items unresolved across two Sundays are downgraded to skim (reduced cost).
     */
    public static List<QueueEntry> recoveryQueue(List<Item> items, Map<Integer, LocalDate> dateOfDay,
                                                 Map<String, Status> status, LocalDate today, List<CalDay> cal) {
        List<LocalDate> sundays = new ArrayList<>();
        for (CalDay c : cal) if (c.type == DayType.SUNDAY) sundays.add(c.date);

        Map<String, QueueEntry> overdue = new HashMap<>();
        List<Item> overdueItems = new ArrayList<>();
        for (Item it : items) {
            LocalDate due = dateOfDay.get(it.day);
            if (due == null || !due.isBefore(today)) continue;
            if (statusOf(status, it.key()) != Status.PENDING) continue;
            int carried = 0;
            for (LocalDate s : sundays) if (s.isAfter(due) && s.isBefore(today)) carried++;
            boolean skim = it.tier == Tier.EXTENDED && carried >= SKIM_AFTER_SUNDAYS;
            double cost = skim ? SKIM_HOURS : it.hours();
            int age = (int) ChronoUnit.DAYS.between(due, today);
            overdue.put(it.key(), new QueueEntry(it, due, age, carried, skim, cost));
            overdueItems.add(it);
        }
        Collections.sort(overdueItems, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                if (a.stream != b.stream) return a.stream == Stream.MATH ? -1 : 1;
                if (a.tier != b.tier) return a.tier.ordinal() - b.tier.ordinal();
                return Integer.compare(a.day, b.day);
            }
        });
        List<QueueEntry> out = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (Item it : overdueItems) emit(it, overdue, emitted, out);
        return out;
    }

    private static void emit(Item it, Map<String, QueueEntry> overdue, Set<String> emitted, List<QueueEntry> out) {
        if (emitted.contains(it.key())) return;
        emitted.add(it.key()); // mark first to be safe against malformed cycles
        List<Integer> pre = new ArrayList<>(it.prereqs);
        Collections.sort(pre);
        for (int p : pre) {
            QueueEntry pe = overdue.get(Item.key(p, it.stream));
            if (pe != null) emit(pe.item, overdue, emitted, out);
        }
        out.add(overdue.get(it.key()));
    }

    // ---------------------------------------------------------------- recovery-day packing

    public static final class Pack {
        public final List<QueueEntry> picked;
        public final List<QueueEntry> leftover;
        public final double hoursUsed;

        Pack(List<QueueEntry> picked, List<QueueEntry> leftover, double hoursUsed) {
            this.picked = picked;
            this.leftover = leftover;
            this.hoursUsed = hoursUsed;
        }
    }

    /** Greedy fill of a recovery day. An item whose prerequisite was skipped is never picked. */
    public static Pack pack(List<QueueEntry> queue, double budgetHours) {
        List<QueueEntry> picked = new ArrayList<>();
        List<QueueEntry> leftover = new ArrayList<>();
        Set<String> skipped = new HashSet<>();
        double used = 0;
        for (QueueEntry q : queue) {
            boolean blocked = false;
            for (int p : q.item.prereqs) if (skipped.contains(Item.key(p, q.item.stream))) blocked = true;
            if (!blocked && used + q.cost <= budgetHours + 1e-9) {
                picked.add(q);
                used += q.cost;
            } else {
                leftover.add(q);
                skipped.add(q.item.key());
            }
        }
        return new Pack(picked, leftover, used);
    }

    // ---------------------------------------------------------------- backlog

    public static double backlogHours(List<QueueEntry> queue) {
        double h = 0;
        for (QueueEntry q : queue) h += q.cost;
        return h;
    }

    public static Level level(double hours) {
        if (hours >= RED_HOURS) return Level.RED;
        if (hours >= YELLOW_HOURS) return Level.YELLOW;
        return Level.OK;
    }

    /**
     * Days the programme would slip if the backlog exceeds all remaining recovery capacity
     * (Sundays and flex days from today onward). Zero means the backlog fits.
     */
    public static int projectedDelayDays(double backlog, LocalDate today, List<CalDay> cal) {
        double capacity = 0;
        for (CalDay c : cal) {
            if (c.date.isBefore(today)) continue;
            if (c.type == DayType.SUNDAY) capacity += SUNDAY_CAP;
            else if (c.type == DayType.FLEX) capacity += FLEX_CAP;
        }
        if (backlog <= capacity) return 0;
        return (int) Math.ceil((backlog - capacity) / 8.0);
    }

    // ---------------------------------------------------------------- spaced reviews

    public static final class Review {
        public final String key;
        public final int interval;
        public final LocalDate due;

        Review(String key, int interval, LocalDate due) {
            this.key = key;
            this.interval = interval;
            this.due = due;
        }

        public String id() { return key + "#" + interval; }
    }

    public static final class ReviewResult {
        public final List<Review> shown;
        public final int extra;
        public final int lapsed;

        ReviewResult(List<Review> shown, int extra, int lapsed) {
            this.shown = shown;
            this.extra = extra;
            this.lapsed = lapsed;
        }
    }

    /**
     * Reviews fall due 1, 7 and 30 days after an item is completed. At most REVIEW_CAP are shown per
     * day (oldest first); reviews overdue by more than REVIEW_LAPSE_DAYS lapse instead of piling up.
     */
    public static ReviewResult dueReviews(Map<String, LocalDate> doneOn, Set<String> completed, LocalDate today) {
        List<Review> due = new ArrayList<>();
        int lapsed = 0;
        for (Map.Entry<String, LocalDate> e : doneOn.entrySet()) {
            for (int interval : REVIEW_INTERVALS) {
                String id = e.getKey() + "#" + interval;
                if (completed.contains(id)) continue;
                LocalDate d = e.getValue().plusDays(interval);
                if (d.isAfter(today)) continue;
                if (ChronoUnit.DAYS.between(d, today) > REVIEW_LAPSE_DAYS) { lapsed++; continue; }
                due.add(new Review(e.getKey(), interval, d));
            }
        }
        Collections.sort(due, new Comparator<Review>() {
            @Override public int compare(Review a, Review b) {
                int c = a.due.compareTo(b.due);
                if (c != 0) return c;
                c = a.key.compareTo(b.key);
                if (c != 0) return c;
                return Integer.compare(a.interval, b.interval);
            }
        });
        int extra = Math.max(0, due.size() - REVIEW_CAP);
        List<Review> shown = new ArrayList<>(due.subList(0, Math.min(REVIEW_CAP, due.size())));
        return new ReviewResult(shown, extra, lapsed);
    }
}
