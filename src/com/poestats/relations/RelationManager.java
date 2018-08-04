package com.poestats.relations;

import com.poestats.Item;
import com.poestats.Main;
import com.poestats.league.LeagueEntry;

import java.util.*;

/**
 * maps indexes and shorthands to currency names and vice versa
 */
public class RelationManager {
    //------------------------------------------------------------------------------------------------------------
    // Class variables
    //------------------------------------------------------------------------------------------------------------

    private Map<Integer, List<Integer>> leagueToIds = new HashMap<>();
    private Map<String, Integer> keyToId = new HashMap<>();


    private Map<String, String> currencyAliasToName = new HashMap<>();
    //private IndexRelations indexRelations = new IndexRelations();
    private Map<String, CategoryEntry> categoryRelations = new HashMap<>();
    private List<String> currentlyIndexingChildKeys = new ArrayList<>();
    private volatile boolean newIndexedItem = false;

    //------------------------------------------------------------------------------------------------------------
    // Initialization
    //------------------------------------------------------------------------------------------------------------

    /**
     * Reads currency and item data from file on object prep
     */
    public boolean init() {
        boolean success;

        success = Main.DATABASE.getCurrencyAliases(currencyAliasToName);
        if (!success) {
            Main.ADMIN.log_("Failed to query currency aliases from database. Shutting down...", 5);
            return false;
        } else if (currencyAliasToName.isEmpty()) {
            Main.ADMIN.log_("Database did not contain any currency aliases. Shutting down...", 5);
            return false;
        }

        success = Main.DATABASE.getCategories(categoryRelations);
        if (!success) {
            Main.ADMIN.log_("Failed to query categories from database. Shutting down...", 5);
            return false;
        } else if (categoryRelations.isEmpty()) {
            Main.ADMIN.log_("Database did not contain any category information", 2);
        }

        success = Main.DATABASE.getItemIds(leagueToIds, keyToId);
        if (!success) {
            Main.ADMIN.log_("Failed to query item ids from database. Shutting down...", 5);
            return false;
        } else if (keyToId.isEmpty()) {
            Main.ADMIN.log_("Database did not contain any item id information", 2);
        }

        if (leagueToIds.isEmpty()) {
            for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
                leagueToIds.putIfAbsent(leagueEntry.getId(), new ArrayList<>());
            }
        } else {
            for (LeagueEntry leagueEntry : Main.LEAGUE_MANAGER.getLeagues()) {
                if (!leagueToIds.containsKey(leagueEntry.getId())) {
                    leagueToIds.put(leagueEntry.getId(), new ArrayList<>());
                }
            }
        }

        return true;
    }

    //------------------------------------------------------------------------------------------------------------
    // Indexing methods
    //------------------------------------------------------------------------------------------------------------

    public Integer indexItem(Item item, Integer leagueId) {
        String uniqueKey = item.getUniqueKey();
        Integer itemId = keyToId.get(uniqueKey);

        // If the item is indexed and the league contains that item, return item's id
        if (itemId != null) {
            List<Integer> idList = leagueToIds.get(leagueId);
            if (idList != null && idList.contains(itemId)) return itemId;
        }

        // If the item was marked not to be indexed
        if (item.isDoNotIndex()) return null;

        // If the same item is currently being processed in the same method in another thread
        if (currentlyIndexingChildKeys.contains(uniqueKey)) return null;
        else currentlyIndexingChildKeys.add(uniqueKey);

        indexCategory(item);

        // Add itemdata to database
        if (itemId == null) {
            // Flip flag that will regenerate the itemdata files
            newIndexedItem = true;

            // Get the category ids related to the item
            CategoryEntry categoryEntry = categoryRelations.get(item.getParentCategory());
            Integer parentCategoryId = categoryEntry.getId();
            Integer childCategoryId = categoryEntry.getChildCategoryId(item.getChildCategory());

            // Add item data to the database and get its id
            itemId = Main.DATABASE.indexItemData(item, parentCategoryId, childCategoryId);
            if (itemId != null) keyToId.put(uniqueKey, itemId);
        }

        // Check if the item's id is present under the league
        List<Integer> idList = leagueToIds.get(leagueId);
        if (idList != null && !idList.contains(itemId)) {
            idList.add(itemId);
            leagueToIds.putIfAbsent(leagueId, idList);

            Main.DATABASE.createLeagueItem(leagueId, itemId);
        }

        // Remove unique key from the list
        currentlyIndexingChildKeys.remove(uniqueKey);

        return itemId;
    }

    /**
     * Manage item category list
     *
     * @param item
     */
    private void indexCategory(Item item) {
        CategoryEntry categoryEntry = categoryRelations.get(item.getParentCategory());

        if (categoryEntry == null) {
            Integer parentId = Main.DATABASE.addParentCategory(item.getParentCategory());
            if (parentId == null) return;

            categoryEntry = new CategoryEntry();
            categoryEntry.setId(parentId);

            categoryRelations.put(item.getParentCategory(), categoryEntry);
        }

        if (item.getChildCategory() != null && !categoryEntry.hasChild(item.getChildCategory())) {
            int parentId = categoryRelations.get(item.getParentCategory()).getId();

            Integer childId = Main.DATABASE.addChildCategory(parentId, item.getChildCategory());
            if (childId == null) return;

            categoryEntry.addChild(item.getChildCategory(), childId);
        }
    }

    //------------------------------------------------------------------------------------------------------------
    // Getters and setters
    //------------------------------------------------------------------------------------------------------------

    public Map<String, String> getCurrencyAliasToName() {
        return currencyAliasToName;
    }

    public Map<String, CategoryEntry> getCategoryRelations() {
        return categoryRelations;
    }

    public boolean isNewIndexedItem() {
        if (newIndexedItem) {
            newIndexedItem = false;
            return true;
        } else return false;
    }
}
