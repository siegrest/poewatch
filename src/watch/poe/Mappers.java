package watch.poe;

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
     * Property object
     */
    public class Property {
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
        int w, h, x, y, ilvl, frameType;
        Boolean identified, corrupted, enchanted;
        String icon, league, id, name, typeLine;
        String note;
        List<Property> properties;
        List<Socket> sockets;
        List<String> explicitMods;

        Map<String, List<String>> category;
        List<String> enchantMods;

        public void fix() {
            id = id.substring(0, 32);
            name = name.substring(name.lastIndexOf(">") + 1);
            enchanted = enchantMods != null;
        }
    }
}