package poe.Statistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poe.Database.Database;
import poe.Interval.TimeFrame;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.toIntExact;

public class StatisticsManager {
    private static final Logger logger = LoggerFactory.getLogger(StatisticsManager.class);
    private final Map<Thread, Set<StatTimer>> threadTimers = new HashMap<>();
    private final Database database;

    // Definitions of all statistics collectors
    private final Collector[] collectors = {
            new Collector(StatType.TIME_API_REPLY_DOWNLOAD,             GroupType.AVG,      TimeFrame.M_60,    null),
            new Collector(StatType.TIME_PARSE_REPLY,                    GroupType.AVG,      TimeFrame.M_60,    null),
            new Collector(StatType.TIME_API_TTFB,                       GroupType.AVG,      TimeFrame.M_60,    null),

            new Collector(StatType.COUNT_API_ERRORS_DUPLICATE,          GroupType.SUM,      TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_API_ERRORS_CONNECT_TIMEOUT,    GroupType.COUNT,    TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_API_ERRORS_READ_TIMEOUT,       GroupType.COUNT,    TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_API_ERRORS_CONN_RESET,         GroupType.COUNT,    TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_API_ERRORS_5XX,                GroupType.COUNT,    TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_API_ERRORS_4XX,                GroupType.COUNT,    TimeFrame.M_60,    null),

            new Collector(StatType.COUNT_REPLY_SIZE,                    GroupType.AVG,      TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_API_CALLS,                     GroupType.COUNT,    TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_TOTAL_STASHES,                 GroupType.SUM,      TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_TOTAL_ITEMS,                   GroupType.SUM,      TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_ACCEPTED_ITEMS,                GroupType.SUM,      TimeFrame.M_60,    null),
            new Collector(StatType.COUNT_ACTIVE_ACCOUNTS,               GroupType.SUM,      TimeFrame.M_60,    null),

            new Collector(StatType.APP_STARTUP,                         GroupType.COUNT,    TimeFrame.M_60,    null),
            new Collector(StatType.APP_SHUTDOWN,                        GroupType.COUNT,    TimeFrame.M_60,    null),
    };

    public StatisticsManager(Database database) {
        this.database = database;

        // Get ongoing statistics collectors from database
        database.stats.getTmpStatistics(collectors);

        // Find if any of the collectors have expired during the time the app was offline
        Set<Collector> expired = Arrays.stream(collectors)
                .filter(Collector::isRecorded)
                .filter(Collector::hasValues)
                .filter(Collector::isExpired)
                .peek(i -> logger.info(String.format("Found expired collector [%s]", i.getType().name())))
                .collect(Collectors.toSet());

        // Delete them from the database, if any
        database.stats.deleteTmpStatistics(expired);
        database.stats.uploadStatistics(expired);

        // Reset the expired collectors, if any
        expired.forEach(Collector::reset);
    }

    /**
     * Initiates a timer with the specified type
     *
     * @param type Stat identifier
     */
    public void startTimer(StatType type) {
        synchronized (threadTimers) {
            Set<StatTimer> statTimerList = threadTimers.getOrDefault(Thread.currentThread(), new HashSet<>());
            threadTimers.putIfAbsent(Thread.currentThread(), statTimerList);

            // If there was no entry, create a new one and add it to the list
            if (statTimerList.stream().anyMatch(i -> i.getType().equals(type))) {
                logger.error("Stat timer already exists");
                throw new RuntimeException();
            }

            statTimerList.add(new StatTimer(type));
        }
    }

    /**
     * Stops the timer instance associated with the provided type and notes the delay
     *
     * @param type Stat identifier
     * @return Delay in MS
     */
    public int clkTimer(StatType type) {
        int delay;

        synchronized (threadTimers) {
            Set<StatTimer> statEntryList = threadTimers.get(Thread.currentThread());

            if (statEntryList == null) {
                logger.error("Thread doesn't exist in current context");
                throw new RuntimeException();
            }

            // Find first timer
            StatTimer statTimer = statEntryList.stream()
                    .filter(i -> i.getType().equals(type))
                    .findFirst()
                    .orElse(null);

            // If it didn't exist
            if (statTimer == null) {
                logger.warn("Stat type doesn't exist in current context");
                return -1;
            }

            // Remove the timer
            statEntryList.remove(statTimer);

            // Get delay as int MS
            delay = toIntExact(System.currentTimeMillis() - statTimer.getStartTime());
        }

        // Add value to the collector
        addValue(type, delay);

        // Return value isn't even used anywhere
        return delay;
    }

    /**
     * Add a value directly to a collector
     *
     * @param type Stat identifier
     * @param val Value to save
     */
    public void addValue(StatType type, Integer val) {
        synchronized (collectors) {
            // Find first collector
            Collector collector = Arrays.stream(collectors)
                    .filter(i -> i.getType().equals(type))
                    .findFirst()
                    .orElse(null);

            // If it didn't exist
            if (collector == null) {
                logger.error("The specified collector could not be found");
                throw new RuntimeException();
            }

            // Add value to the collector
            collector.addValue(val);
        }
    }

    /**
     * Uploads all latest timer delays to database
     */
    public void upload() {
        // Find collectors that are expired
        Set<Collector> expired = Arrays.stream(collectors)
                .filter(Collector::isRecorded)
                .filter(Collector::hasValues)
                .filter(Collector::isExpired)
                .collect(Collectors.toSet());

        // Find collectors that are still ongoing
        Set<Collector> unexpired = Arrays.stream(collectors)
                .filter(Collector::isRecorded)
                .filter(Collector::hasValues)
                .filter(i -> !i.isExpired())
                .collect(Collectors.toSet());

        database.stats.uploadStatistics(expired);
        database.stats.deleteTmpStatistics(expired);
        database.stats.uploadTempStatistics(unexpired);

        // Delete old stat entries from database
        database.stats.trimStatHistory(collectors);

        // Reset all expired collectors
        expired.forEach(Collector::reset);
    }

    /**
     * Gets latest value with the specified type
     *
     * @param type Stat identifier
     * @return The value or 0
     */
    public int getLast(StatType type) {
        if (type == null) {
            logger.error("StatType cannot be null");
            throw new RuntimeException();
        }

        // Find first collector
        Collector collector = Arrays.stream(collectors)
                .filter(i -> i.getType().equals(type))
                .findFirst()
                .orElse(null);

        // If it didn't exist
        if (collector == null) {
            logger.error("The specified collector could not be found");
            throw new RuntimeException();
        }

        return collector.getLatestValue();
    }
}
