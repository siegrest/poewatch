package com.poestats.pricer;

import com.google.gson.Gson;
import com.poestats.*;
import com.poestats.database.Database;
import com.poestats.league.LeagueEntry;
import com.poestats.pricer.entries.RawEntry;
import com.poestats.pricer.maps.CurrencyMaps.*;
import com.poestats.pricer.maps.RawMaps.*;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class EntryManager {
    //------------------------------------------------------------------------------------------------------------
    // Class variables
    //------------------------------------------------------------------------------------------------------------

    private Map<String, List<Integer>> leagueToIds = new HashMap<>();

    private CurrencyLeagueMap currencyLeagueMap;

    //private final Object monitor = new Object();
    private Gson gson;

    // private volatile boolean flagPause;
    private final StatusElement status = new StatusElement();

    //------------------------------------------------------------------------------------------------------------
    // Main methods
    //------------------------------------------------------------------------------------------------------------

    /**
     * Loads data in from file on object initialization
     */
    public void init() {
        loadStartParameters();
        loadCurrency();
    }

    /**
     * Loads status data from file on program start
     */
    private void loadStartParameters() {
        boolean querySuccessful = Main.DATABASE.getStatus(status);
        if (!querySuccessful) {
            Main.ADMIN.log_("Could not query status from database", 5);
            System.exit(-1);
        }

        fixCounters();

        String tenMinDisplay = "[10m:" + String.format("%3d", 10 - (System.currentTimeMillis() - status.tenCounter) / 60000) + " min]";
        String resetTimeDisplay = "[1h:" + String.format("%3d", 60 - (System.currentTimeMillis() - status.sixtyCounter) / 60000) + " min]";
        String twentyHourDisplay = "[24h:" + String.format("%5d", 1440 - (System.currentTimeMillis() - status.twentyFourCounter) / 60000) + " min]";
        Main.ADMIN.log_("Loaded params: " + tenMinDisplay + resetTimeDisplay + twentyHourDisplay, -1);

        Main.DATABASE.updateStatus(status);
    }

    //------------------------------------------------------------------------------------------------------------
    // Methods for multi-db file structure
    //------------------------------------------------------------------------------------------------------------

    /**
     * Loads in currency rates on program start
     */
    private void loadCurrency() {
        currencyLeagueMap = new CurrencyLeagueMap();

        for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
            String league = leagueEntry.getName();

            CurrencyMap currencyMap = currencyLeagueMap.getOrDefault(league, new CurrencyMap());
            Main.DATABASE.getCurrency(league, currencyMap);
            currencyLeagueMap.putIfAbsent(league, currencyMap);
        }
    }

    /**
     * Writes all collected data to database
     */
    private void cycle() {
        Map<String, List<Integer>> leagueToIds = this.leagueToIds;
        this.leagueToIds = new HashMap<>();

        for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
            String league = leagueEntry.getName();

            List<Integer> idList = leagueToIds.get(league);
            List<Integer> ignoreList = new ArrayList<>();

            if (idList != null) {
                Main.DATABASE.removeItemOutliers(league, idList, ignoreList);

                Main.DATABASE.calculateMean(league, idList, ignoreList);
                Main.DATABASE.calculateMedian(league, idList, ignoreList);
                Main.DATABASE.calculateMode(league, idList, ignoreList);
                Main.DATABASE.removeOldItemEntries(league, idList);
            }

            Main.DATABASE.calculateExalted(league);
            Main.DATABASE.addMinutely(league);
            Main.DATABASE.removeOldHistoryEntries(league, 1, Config.sql_interval_1h);
        }

        if (status.isSixtyBool()) {
            for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
                String league = leagueEntry.getName();

                Main.DATABASE.removeOldHistoryEntries(league, 2, Config.sql_interval_1d);
                Main.DATABASE.addHourly(league);
                Main.DATABASE.calcQuantity(league);
            }
        }

        if (status.isTwentyFourBool()) {
            for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
                String league = leagueEntry.getName();

                Main.DATABASE.addDaily(league);
                Main.DATABASE.removeOldHistoryEntries(league, 3, Config.sql_interval_7d);
            }
        }
    }

    /*private void generateOutputFiles() {
        List<String> oldOutputFiles = new ArrayList<>();
        List<String> newOutputFiles = new ArrayList<>();

        Main.DATABASE.getOutputFiles(oldOutputFiles);
        Config.folder_newOutput.mkdirs();

        for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
            String league = Database.formatLeague(leagueEntry.getName());

            for (String category : Main.RELATIONS.getCategories().keySet()) {
                Map<String, ParcelEntry> tmpParcel = new LinkedHashMap<>();

                Main.DATABASE.getOutputItems(league, category, tmpParcel);
                Main.DATABASE.getOutputHistory(league, tmpParcel);

                List<ParcelEntry> parcel = new ArrayList<>();
                for (ParcelEntry parcelEntry : tmpParcel.values()) {
                    parcelEntry.calcSpark();
                    parcel.add(parcelEntry);
                }

                String fileName = league + "_" + category + "_" + System.currentTimeMillis() + ".json";
                File outputFile = new File(Config.folder_newOutput, fileName);

                try (Writer writer = Misc.defineWriter(outputFile)) {
                    if (writer == null) throw new IOException();
                    gson.toJson(parcel, writer);
                } catch (IOException ex) {
                    Main.ADMIN._log(ex, 4);
                    Main.ADMIN.log_("Couldn't write output JSON to file", 3);
                }

                try {
                    String path = outputFile.getCanonicalPath();
                    newOutputFiles.add(path);
                    Main.DATABASE.addOutputFile(league, category, path);
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Main.ADMIN.log_("Couldn't get file's actual path", 3);
                }
            }
        }

        File[] outputFiles = Config.folder_newOutput.listFiles();
        if (outputFiles == null) return;

        try {
            for (File outputFile : outputFiles) {
                if (oldOutputFiles.contains(outputFile.getCanonicalPath())) continue;
                if (newOutputFiles.contains(outputFile.getCanonicalPath())) continue;

                boolean success = outputFile.delete();
                if (!success) Main.ADMIN.log_("Could not delete old output file", 3);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            Main.ADMIN.log_("Could not delete old output files", 3);
        }
    }*/

    //------------------------------------------------------------------------------------------------------------
    // Often called controller methods
    //------------------------------------------------------------------------------------------------------------

    /**
     * Main loop of the pricing service. Can be called whenever, only runs after specific amount of time has passed
     */
    public void run() {
        long current = System.currentTimeMillis();

        // Run every minute (-ish)
        if (current - status.lastRunTime < Config.entryController_sleepMS) return;
        status.lastRunTime = System.currentTimeMillis();

        // Raise static flag that suspends other threads while the databases are being worked on
        // flipPauseFlag();

        // Allow workers to pause
        try { Thread.sleep(50); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }

        // Run once every 10min
        if (current - status.tenCounter > Config.entryController_tenMS) {
            status.tenCounter += (current - status.tenCounter) / Config.entryController_tenMS * Config.entryController_tenMS;
            status.setTenBool(true);
            Main.ADMIN.log_("10 activated", 0);

            Main.LEAGUE_MANAGER.download();
        }

        // Run once every 60min
        if (current - status.sixtyCounter > Config.entryController_sixtyMS) {
            status.sixtyCounter += (current - status.sixtyCounter) / Config.entryController_sixtyMS * Config.entryController_sixtyMS;
            status.setSixtyBool(true);
            Main.ADMIN.log_("60 activated", 0);
        }

        // Run once every 24h
        if (current - status.twentyFourCounter > Config.entryController_twentyFourMS) {
            if (status.twentyFourCounter == 0) status.twentyFourCounter -= Config.entryController_counterOffset;
            status.twentyFourCounter += (current - status.twentyFourCounter) / Config.entryController_twentyFourMS * Config.entryController_twentyFourMS ;
            status.setTwentyFourBool(true);
            Main.ADMIN.log_("24 activated", 0);
        }

        // Sort JSON
        long time_cycle = System.currentTimeMillis();
        cycle();
        time_cycle = System.currentTimeMillis() - time_cycle;

        // Get latest currency rates
        long time_load_currency = System.currentTimeMillis();
        loadCurrency();
        time_load_currency = System.currentTimeMillis() - time_load_currency;

        // Build JSON
        long time_json = System.currentTimeMillis();
        /*generateOutputFiles();*/
        time_json = System.currentTimeMillis() - time_json;

        // Prepare message
        String timeElapsedDisplay = "[Took:" + String.format("%5d", System.currentTimeMillis() - status.lastRunTime) + " ms]";
        String tenMinDisplay = "[10m:" + String.format("%3d", 10 - (System.currentTimeMillis() - status.tenCounter) / 60000) + " min]";
        String resetTimeDisplay = "[1h:" + String.format("%3d", 60 - (System.currentTimeMillis() - status.sixtyCounter) / 60000) + " min]";
        String twentyHourDisplay = "[24h:" + String.format("%5d", 1440 - (System.currentTimeMillis() - status.twentyFourCounter) / 60000) + " min]";
        String timeTookDisplay = "(Cycle:" + String.format("%5d", time_cycle) + " ms)(JSON:" + String.format("%5d", time_json) +
                " ms)(currency:" + String.format("%5d", time_load_currency) + " ms)";
        Main.ADMIN.log_(timeElapsedDisplay + tenMinDisplay + resetTimeDisplay + twentyHourDisplay + timeTookDisplay, -1);

        // Switch off flags
        status.setTwentyFourBool(false);
        status.setSixtyBool(false);
        status.setTenBool(false);
        // flipPauseFlag();

        Main.DATABASE.updateStatus(status);
    }

    /**
     * Adds entries to the databases
     *
     * @param reply APIReply object that a worker has downloaded and deserialized
     */
    public void parseItems(Mappers.APIReply reply) {
        RawEntryLeagueMap leagueToIdToAccountToRawEntry = new RawEntryLeagueMap();

        for (Mappers.Stash stash : reply.stashes) {
            String league = null;

            for (Item item : stash.items) {
                if (league == null) league = item.getLeague();

                item.fix();
                item.parseItem();
                if (item.isDiscard()) continue;

                Integer id = Main.RELATIONS.indexItem(item, league);
                if (id == null) continue;

                RawEntry rawEntry = new RawEntry();
                rawEntry.load(item);

                boolean discard = rawEntry.convertPrice(currencyLeagueMap.get(league));
                if (discard) continue; // Couldn't convert the listed currency to chaos

                IndexMap idToAccountToRawEntry = leagueToIdToAccountToRawEntry.getOrDefault(league, new IndexMap());
                AccountMap accountToRawEntry = idToAccountToRawEntry.getOrDefault(id, new AccountMap());

                accountToRawEntry.put(stash.accountName, rawEntry);

                idToAccountToRawEntry.putIfAbsent(id, accountToRawEntry);
                leagueToIdToAccountToRawEntry.putIfAbsent(league, idToAccountToRawEntry);

                // Maintain a list of league-specific item IDs that have had entries added to them
                List<Integer> idList = leagueToIds.getOrDefault(league, new ArrayList<>());
                if (!idList.contains(id)) idList.add(id);
                leagueToIds.putIfAbsent(league, idList);
            }
        }

        for (String league : leagueToIdToAccountToRawEntry.keySet()) {
            IndexMap idToAccountToRawEntry = leagueToIdToAccountToRawEntry.get(league);

            Main.DATABASE.uploadRaw(league, idToAccountToRawEntry);
            Main.DATABASE.updateCounters(league, idToAccountToRawEntry);
        }
    }

    /**
     * Makes sure counters don't fall behind
     */
    private void fixCounters() {
        long current = System.currentTimeMillis();

        if (current - status.tenCounter > Config.entryController_tenMS) {
            long gap = (current - status.tenCounter) / Config.entryController_tenMS * Config.entryController_tenMS;
            status.tenCounter += gap;
        }

        if (current - status.sixtyCounter > Config.entryController_sixtyMS) {
            long gap = (current - status.sixtyCounter) / Config.entryController_sixtyMS * Config.entryController_sixtyMS;
            status.sixtyCounter += gap;
        }

        if (current - status.twentyFourCounter > Config.entryController_twentyFourMS) {
            if (status.twentyFourCounter == 0) status.twentyFourCounter -= Config.entryController_counterOffset;
            long gap = (current - status.twentyFourCounter) / Config.entryController_twentyFourMS * Config.entryController_twentyFourMS;
            status.twentyFourCounter += gap;
        }
    }

    //------------------------------------------------------------------------------------------------------------
    // Getters and setters
    //------------------------------------------------------------------------------------------------------------

    public StatusElement getStatus() {
        return status;
    }

    public void setGson(Gson gson) {
        this.gson = gson;
    }
}
