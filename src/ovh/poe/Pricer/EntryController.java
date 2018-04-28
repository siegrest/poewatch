package ovh.poe.Pricer;

import com.google.gson.Gson;
import ovh.poe.Mappers;
import ovh.poe.Item;
import ovh.poe.Main;
import ovh.poe.Misc;

import java.io.*;
import java.util.*;

/**
 * Manages database
 */
public class EntryController {
    // League map. Has mappings of: [league name - category map]
    static class LeagueMap extends HashMap<String, CategoryMap> { }
    // Category map. Has mappings of: [category name - index map]
    static class CategoryMap extends HashMap<String, IndexMap> { }
    // Index map. Has mappings of: [index - Entry]
    static class IndexMap extends HashMap<String, Entry> { }

    private final LeagueMap leagueMap = new LeagueMap();

    private final JSONParcel JSONParcel = new JSONParcel();
    private final Object monitor = new Object();
    private final Gson gson = Main.getGson();

    private long lastRunTime = System.currentTimeMillis();
    public volatile boolean flagPause, tenBool, sixtyBool, twentyFourBool;
    private long twentyFourCounter, sixtyCounter, tenCounter;

    //------------------------------------------------------------------------------------------------------------
    // Upon starting/stopping the program
    //------------------------------------------------------------------------------------------------------------

    /**
     * Loads data in from file on object initialization
     */
    public EntryController() {
        loadStartParameters();
        loadDatabases();
    }

    /**
     * Saves current status data in text file
     */
    private void saveStartParameters() {
        File paramFile = new File("./data/status.csv");

        try (BufferedWriter writer = Misc.defineWriter(paramFile)) {
            if (writer == null) return;

            String buffer = "writeTime: " + System.currentTimeMillis() + "\n";
            buffer += "twentyFourCounter: " + twentyFourCounter + "\n";
            buffer += "sixtyCounter: " + sixtyCounter + "\n";
            buffer += "tenCounter: " + tenCounter + "\n";

            writer.write(buffer);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Loads status data from file on program start
     */
    private void loadStartParameters() {
        File paramFile = new File("./data/status.csv");

        try (BufferedReader reader = Misc.defineReader(paramFile)) {
            if (reader == null) return;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] splitLine = line.split(": ");

                switch (splitLine[0]) {
                    case "twentyFourCounter":
                        twentyFourCounter = Long.parseLong(splitLine[1]);
                        break;
                    case "sixtyCounter":
                        sixtyCounter = Long.parseLong(splitLine[1]);
                        break;
                    case "tenCounter":
                        tenCounter = Long.parseLong(splitLine[1]);
                        break;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    //------------------------------------------------------------------------------------------------------------
    // Methods for multi-db file structure
    //------------------------------------------------------------------------------------------------------------

    /**
     * Load in currency data on app start and fill leagueMap with leagues
     */
    private void loadDatabases() {
        for (String league : Main.RELATIONS.getLeagues()) {
            File currencyFile = new File("./data/database/"+league+"/currency.csv");

            if (!currencyFile.exists()) {
                System.out.println("Missing currency file for league: " + league);
                continue;
            }

            try (BufferedReader reader = Misc.defineReader(currencyFile)) {
                if (reader == null) {
                    System.out.println("Could not create currency reader for: " + league);
                    continue;
                }

                CategoryMap categoryMap = leagueMap.getOrDefault(league, new CategoryMap());
                IndexMap indexMap = categoryMap.getOrDefault("currency", new IndexMap());

                String line;
                while ((line = reader.readLine()) != null) {
                    String index = line.substring(0, line.indexOf("::"));
                    Entry entry = new Entry(line, league);
                    indexMap.put(index, entry);
                }

                categoryMap.putIfAbsent("currency", indexMap);
                leagueMap.putIfAbsent(league, categoryMap);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    /**
     * Writes all collected data to file
     */
    private void cycle() {
        for (String league : Main.RELATIONS.getLeagues()) {
            CategoryMap categoryMap = leagueMap.getOrDefault(league, new CategoryMap());
            File leagueFolder = new File("./data/database/"+league+"/");

            if (!leagueFolder.exists()) {
                System.out.println("[asdf] Missing folder for league '"+league+"'");
                leagueFolder.mkdirs();
            }

            for (String category : Main.RELATIONS.getCategories().keySet()) {
                IndexMap indexMap = categoryMap.getOrDefault(category, new IndexMap());
                Set<String> tmp_unparsedIndexes = new HashSet<>(indexMap.keySet());
                File leagueFile = new File("./data/database/"+league+"/"+category+".csv");

                // The file data will be stored in
                File tmpLeagueFile = new File("./data/database/"+league+"/"+category+".tmp");

                // Define IO objects
                BufferedReader reader = Misc.defineReader(leagueFile);
                BufferedWriter writer = Misc.defineWriter(tmpLeagueFile);
                if (writer == null) continue;

                if (reader != null) {
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String index = line.substring(0, line.indexOf("::"));
                            Entry entry;

                            if (indexMap.containsKey(index)) {
                                // Remove processed indexes from the set so that only entries that were found in the map
                                // but not the file will remain
                                tmp_unparsedIndexes.remove(index);

                                if (category.equals("currency")) {
                                    entry = indexMap.getOrDefault(index, new Entry(line, league));
                                } else {
                                    entry = indexMap.remove(index);
                                    entry.parseLine(line);
                                }
                            } else {
                                entry = new Entry(line, league);
                            }

                            entry.setLeague(league);
                            entry.cycle();
                            JSONParcel.add(entry);

                            String writeLine = entry.buildLine();
                            if (writeLine == null) Main.ADMIN.log_("Deleted entry: " + entry.getIndex(), 0);
                            else writer.write(writeLine);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } else {
                    System.out.println("Missing database '"+category+"' for '"+league+"'");
                }

                try {
                    for (String index : tmp_unparsedIndexes) {
                        Entry entry;

                        if (category.equals("currency")) entry = indexMap.get(index);
                        else entry = indexMap.remove(index);

                        entry.setLeague(league);
                        entry.cycle();
                        JSONParcel.add(entry);

                        String writeLine = entry.buildLine();
                        if (writeLine == null) Main.ADMIN.log_("Deleted entry: "+entry.getIndex(), 0);
                        else writer.write(writeLine);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                // Close file
                try {
                    if (reader != null) reader.close();
                    writer.flush();
                    writer.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                // Remove original file
                if (leagueFile.exists() && !leagueFile.delete()) {
                    String errorMsg = "Unable to remove '"+league+"/"+category+"/"+leagueFile.getName()+"'";
                    Main.ADMIN.log_(errorMsg, 4);
                }
                // Rename temp file to original file
                if (tmpLeagueFile.exists() && !tmpLeagueFile.renameTo(leagueFile)) {
                    String errorMsg = "Unable to rename '"+league+"/"+category+"/"+tmpLeagueFile.getName()+"'";
                    Main.ADMIN.log_(errorMsg, 4);
                }
            }
        }
    }

    //------------------------------------------------------------------------------------------------------------
    // Often called controller methods
    //------------------------------------------------------------------------------------------------------------

    /**
     * Main loop of the pricing service. Can be called whenever, only runs after specific amount of time has passed
     */
    public void run() {
        // Run every minute (-ish)
        if (System.currentTimeMillis() - lastRunTime < Main.CONFIG.pricerControllerSleepCycle * 1000) return;
        // Don't run if there hasn't been a successful run in the past 30 seconds
        if ((System.currentTimeMillis() - Main.ADMIN.changeIDElement.lastUpdate) / 1000 > 30) return;

        // Raise static flag that suspends other threads while the databases are being worked on
        flipPauseFlag();

        // Run once every 10min
        if ((System.currentTimeMillis() - tenCounter) > 3600000) {
            if (tenCounter - System.currentTimeMillis() < 1) tenCounter = System.currentTimeMillis();
            else tenCounter += 10 * 60 * 1000;

            tenBool = true;
        }

        // Run once every 60min
        if ((System.currentTimeMillis() - sixtyCounter) > 3600000) {
            if (sixtyCounter - System.currentTimeMillis() < 1) sixtyCounter = System.currentTimeMillis();
            else sixtyCounter += 60 * 60 * 1000;

            sixtyBool = true;

            // Get a list of active leagues from pathofexile.com's api
            Main.RELATIONS.downloadLeagueList();
        }

        // Run once every 24h
        if ((System.currentTimeMillis() - twentyFourCounter) > 86400000) {
            if (twentyFourCounter - System.currentTimeMillis() < 1) twentyFourCounter = System.currentTimeMillis();
            else twentyFourCounter += 24 * 60 * 60 * 1000;

            twentyFourBool = true;
        }

        // The method that does it all
        long time_cycle = System.currentTimeMillis();
        cycle();
        time_cycle = System.currentTimeMillis() - time_cycle;

        // Sort JSON
        long time_sort = System.currentTimeMillis();
        JSONParcel.sort();
        time_sort = System.currentTimeMillis() - time_sort;

        // Build JSON
        long time_json = System.currentTimeMillis();
        writeJSONToFile();
        time_json = System.currentTimeMillis() - time_json;

        // Prepare message
        String timeElapsedDisplay = "[Took:" + String.format("%4d", (System.currentTimeMillis() - lastRunTime) / 1000) + " sec]";
        String resetTimeDisplay = "[1h:" + String.format("%3d", 60 - (System.currentTimeMillis() - sixtyCounter) / 60000) + " min]";
        String twentyHourDisplay = "[24h:" + String.format("%5d", 1440 - (System.currentTimeMillis() - twentyFourCounter) / 60000) + " min]";
        String timeTookDisplay = " (Cycle:" + String.format("%5d", time_cycle) + " ms) (JSON:" + String.format("%5d", time_json) + " ms) (sort:" + String.format("%5d", time_sort) + " ms)";
        Main.ADMIN.log_(timeElapsedDisplay + resetTimeDisplay + twentyHourDisplay + timeTookDisplay, -1);

        // Set last run time
        lastRunTime = System.currentTimeMillis();

        Main.RELATIONS.saveData();

        // Backup output folder
        if (twentyFourBool) {
            long time_backup = System.currentTimeMillis();
            Main.ADMIN.backup(new File("./data/output"), "daily");
            time_backup = System.currentTimeMillis() - time_backup;
            Main.ADMIN.log_("Backup took: " + time_backup + " ms", 0);
        }

        // Clear the parcel
        JSONParcel.clear();

        // Switch off flags
        tenBool = sixtyBool = twentyFourBool = false;
        flipPauseFlag();

        saveStartParameters();
    }

    /**
     * Adds entries to the databases
     *
     * @param reply APIReply object that a Worker has downloaded and deserialized
     */
    public void parseItems(Mappers.APIReply reply) {
        // Loop through every single item, checking every single one of them
        for (Mappers.Stash stash : reply.stashes) {
            stash.fix();
            for (Item item : stash.items) {
                // Snooze. The lock will be lifted in about 0.1 seconds. This loop is NOT time-sensitive
                while (flagPause) {
                    synchronized (monitor) {
                        try {
                            monitor.wait(500);
                        } catch (InterruptedException ex) {
                        }
                    }
                }

                item.fix();
                item.parseItem();
                if (item.discard) continue;

                CategoryMap categoryMap = leagueMap.getOrDefault(item.league, new CategoryMap());
                IndexMap indexMap = categoryMap.getOrDefault(item.parentCategory, new IndexMap());

                String index = Main.RELATIONS.indexItem(item);
                if (index == null) continue; // Some currency items have invalid icons

                Entry entry = indexMap.getOrDefault(index, new Entry());
                entry.add(item, stash.accountName, index);

                indexMap.putIfAbsent(index, entry);
                categoryMap.putIfAbsent(item.parentCategory, indexMap);
                leagueMap.putIfAbsent(item.league, categoryMap);
            }
        }
    }

    /**
     * Switches pause boolean from state to state and wakes monitor
     */
    private void flipPauseFlag() {
        synchronized (monitor) {
            flagPause = !flagPause;
            monitor.notifyAll();
        }
    }

    /**
     * Writes JSONParcel object to JSON file
     */
    private void writeJSONToFile() {
        for (String league : JSONParcel.getJsonLeagueMap().keySet()) {
            JSONParcel.JSONCategoryMap jsonCategoryMap = JSONParcel.getJsonLeagueMap().get(league);

            for (String category : jsonCategoryMap.keySet()) {
                JSONParcel.JSONItemList jsonItems = jsonCategoryMap.get(category);

                try {
                    new File("./data/output/"+league).mkdirs();
                    File file = new File("./data/output/"+league+"/"+category+".json");

                    BufferedWriter writer = Misc.defineWriter(file);
                    if (writer == null) throw new IOException("File '"+league+"' error");

                    gson.toJson(jsonItems, writer);

                    writer.flush();
                    writer.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

            }
        }
    }

    //------------------------------------------------------------------------------------------------------------
    // Getters and setters
    //------------------------------------------------------------------------------------------------------------

    public IndexMap getCurrencyMap (String league) {
        CategoryMap categoryMap = leagueMap.getOrDefault(league, null);
        if (categoryMap == null) return null;

        IndexMap indexMap = categoryMap.getOrDefault("currency", null);
        if (indexMap == null) return null;

        return indexMap;
    }
}
