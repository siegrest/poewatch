package ovh.poe;

import ovh.poe.Pricer.DataEntry;

import java.util.*;

/**
 * Holds all the mapper classes. These are not used for anything other than to map JSON strings to objects and
 * therefore will not likely to be changed at all. Each of these classes is similar in  structure - they all have
 * nothing but variables, getters and setters. If additional functionality is required, it is advised to extend
 * these classes.
 */
public class Mappers {
    /**
     * Complete API reply
     */
    public class APIReply {
        public String next_change_id;
        public List<Stash> stashes;
    }

    /**
     * Stash object
     */
    public class Stash {
        public String id;
        public String accountName;
        public String stash;
        public String lastCharacterName;
        public List<Item> items;

        public void fix() {
            if (accountName != null)
                accountName = accountName.replaceAll("[^A-Za-z0-9]", "_");
            if (lastCharacterName != null)
                lastCharacterName = lastCharacterName.replaceAll("[^A-Za-z0-9]", "_");
        }
    }


    /**
     * Universal deserializer for poe.ninja, poe.rates and pathofexile.com/api
     */
    public class ChangeID {
        public String next_change_id;
        public String changeId;
        public String psapi;

        public String get() {
            if (next_change_id != null) return next_change_id;
            else if (changeId != null) return changeId;
            else return psapi;
        }
    }

    /**
     * Properties object
     */
    public class Properties {
        public String name;
        public List<List<String>> values;
    }

    /**
     * Socket object
     */
    public class Socket {
        public int group;
        public String attr;
    }

    /**
     * Item object
     */
    public static class BaseItem {
        public int w, h, x, y, ilvl, frameType;
        public boolean identified, corrupted, enchanted;
        public String icon, league, id, name, typeLine;
        public String note;
        public List<Properties> properties;
        public List<Socket> sockets;
        public List<String> explicitMods;

        // This field varies in the API and cannot be assigned a specific type. A few examples can be seen below:
        // "category": {"jewels": []}
        // "category": {"armour": ["gloves"]}
        public Object category;
        public Object enchantMods;

        public void fix() {
            id = id.substring(0, 16);
            name = name.substring(name.lastIndexOf(">") + 1);
            enchanted = enchantMods != null;
        }
    }

    /**
     * Serializable output
     */
    public static class JSONParcel {
        public static class Item {
            public double mean, median, mode;
            public int count, inc, index;
            public String category, icon;

            public void copy (DataEntry entry, String childCategoryName) {
                if (childCategoryName != null) category = childCategoryName;

                mean = entry.getMean();
                median = entry.getMedian();
                mode = entry.getMode();
                count = entry.getCount() + entry.getInc_counter();
                inc = entry.getInc_counter();
                if (entry.getIconIndex() >= 0) {
                    index = entry.getIconIndex();

                    if (Main.RELATIONS.iconIndexToIcon.containsKey(index))
                        icon = Main.RELATIONS.iconIndexToIcon.get(index).url;
                }
            }
        }

        public Map<String, Map<String, Map<String, Item>>> leagues = new TreeMap<>();

        public void add(DataEntry entry) {
            // "Hardcore Bestiary|currency:orbs|Orb of Transmutation|5"
            String[] splitKey = entry.getKey().split("\\|");
            String leagueName = splitKey[0];

            String[] splitCategoryName = splitKey[1].split(":");
            String parentCategoryName = splitCategoryName[0];
            String childCategoryName = null;
            if (splitCategoryName.length > 1) childCategoryName = splitCategoryName[1];

            // "Orb of Transmutation|5"
            String itemName = "";
            for (int i = 2; i < splitKey.length; i++) itemName += splitKey[i] + "|";
            itemName = itemName.substring(0, itemName.length() - 1);

            if (!this.leagues.containsKey(leagueName)) this.leagues.put(leagueName, new TreeMap<>());
            Map<String, Map<String, Item>> league = this.leagues.get(leagueName);

            if (!league.containsKey(parentCategoryName)) league.put(parentCategoryName, new LinkedHashMap<>());
            Map<String, Item> category = league.get(parentCategoryName);

            if (!category.containsKey(itemName)) category.put(itemName, new Item());
            Item item = category.get(itemName);

            item.copy(entry, childCategoryName);
        }

        public void sort() {
            for (String leagueKey : leagues.keySet()) {
                Map<String, Map<String, Item>> league = leagues.get(leagueKey);

                for (String categoryKey : league.keySet()) {
                    Map<String, Item> category = league.get(categoryKey);
                    Map<String, Item> sortedCategory = new LinkedHashMap<>();

                    while (!category.isEmpty()) {
                        Item mostExpensiveItem = new Item();
                        String mostExpensiveItemKey = "";

                        for (String itemKey : category.keySet()) {
                            Item item = category.get(itemKey);

                            if (mostExpensiveItem.median <= item.median){
                                mostExpensiveItem = item;
                                mostExpensiveItemKey = itemKey;
                            }
                        }

                        // If statement not really needed
                        if (!mostExpensiveItemKey.equals("")) category.remove(mostExpensiveItemKey);
                        sortedCategory.put(mostExpensiveItemKey, mostExpensiveItem);
                    }

                    // Write sortedCategory to category map
                    league.put(categoryKey, sortedCategory);
                }
            }
        }

        public void clear () {
            leagues.clear();
        }
    }

    public static class CurrencyRelation {
        String name, index;
        String[] aliases;
    }

    public static class IconRelation {
        String name, url;

        public IconRelation (String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public static class HourlyEntry {
        public double mean, median, mode;

        public HourlyEntry (double mean, double median, double mode) {
            this.mean = mean;
            this.median = median;
            this.mode = mode;
        }
    }

    public static class ItemEntry {
        public double price;
        public String accountName, id;

        public ItemEntry (double price, String accountName, String id) {
            this.price = price;
            this.accountName = accountName;
            this.id = id;
        }
    }

    public static class LeagueListElement {
        public String id, startAt, endAt;
    }
}
