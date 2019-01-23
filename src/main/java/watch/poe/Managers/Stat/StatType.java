package poe.Managers.Stat;

public enum StatType {
    CYCLE_TOTAL,
    CALC_PRICES,
    UPDATE_COUNTERS,
    CALC_EXALT,
    CYCLE_LEAGUES,
    ADD_HOURLY,
    CALC_DAILY,
    RESET_COUNTERS,
    REMOVE_OLD_ENTRIES,
    ADD_DAILY,
    CALC_SPARK,
    ACCOUNT_CHANGES,
    API_CALLS,

    APP_STARTUP,
    APP_SHUTDOWN,

    WORKER_DUPLICATE_JOB,
    WORKER_DOWNLOAD,
    WORKER_PARSE,
    WORKER_UPLOAD_ACCOUNTS,
    WORKER_RESET_STASHES,
    WORKER_UPLOAD_ENTRIES,
    WORKER_UPLOAD_USERNAMES,

    TOTAL_STASHES,
    TOTAL_ITEMS,
    ACCEPTED_ITEMS,
    ACTIVE_ACCOUNTS
}
