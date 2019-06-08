package poe.Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poe.Item.ApiDeserializers.ApiItem;
import poe.Item.Branches.BaseBranch;
import poe.Item.Branches.EnchantBranch;
import poe.Item.Category.CategoryEnum;
import poe.Item.Category.GroupEnum;
import poe.Main;
import poe.Managers.RelationManager;

import java.util.List;

public class Item {
    private static final Logger logger = LoggerFactory.getLogger(Item.class);
    protected static RelationManager relationManager;

    protected ApiItem apiItem;
    protected String name, typeLine, icon;
    protected Integer links, gemLevel, gemQuality, mapTier, itemLevel, stackSize, maxStackSize, series;
    protected Boolean gemCorrupted, shaper, elder;
    protected int frameType;
    protected VariantEnum variation;
    protected CategoryEnum category = null;
    protected GroupEnum group = null;
    protected boolean discard;
    protected Float enchantMin, enchantMax;
    private Key key;

    public Item() {
    }


    /**
     * Removes any unnecessary fields from the item's icon
     *
     * @return Formatted icon URL
     */
    public String formatIconURL() {
        String[] splitURL = apiItem.getIcon().split("\\?", 2);
        String fullIcon = splitURL[0];

        if (splitURL.length > 1) {
            StringBuilder paramBuilder = new StringBuilder();

            for (String param : splitURL[1].split("&")) {
                String[] splitParam = param.split("=");

                switch (splitParam[0]) {
                    case "scale":
                    case "w":
                    case "h":
                    case "mr": // shaped
                    case "mn": // background
                    case "mt": // tier
                    case "relic":
                        paramBuilder.append("&");
                        paramBuilder.append(splitParam[0]);
                        paramBuilder.append("=");
                        paramBuilder.append(splitParam[1]);
                        break;
                    default:
                        break;
                }
            }

            // If there are parameters that should be kept, add them to fullIcon
            if (paramBuilder.length() > 0) {
                // Replace the first "&" symbol with "?"
                paramBuilder.setCharAt(0, '?');
                fullIcon += paramBuilder.toString();
            }
        }

        return fullIcon;
    }

    /**
     * Parses item and calculates various things
     */
    public void process() {
        name = apiItem.getName();
        typeLine = apiItem.getTypeLine();
        frameType = apiItem.getFrameType();
        icon = apiItem.getIcon();

        // Use typeLine as name if name is missing
        if (name == null || name.equals("") || frameType == 2) {
            name = typeLine;
            typeLine = null;
        }

        // Remove formatting string from name
        if (name.contains(">")) {
            name = name.substring(name.lastIndexOf(">") + 1);
        }

        // Find out the item's category and group (eg armour/belt/weapon etc)
        findCategory();

        if (!discard && (category == null || group == null)) {
            String msg = "name:" + name + "|type:" + typeLine + "|frame:" + frameType + "|ilvl:" + itemLevel + "|links:" + links
                    + "|mapTier:" + mapTier + "|var:" + variation + "|lvl:" + gemLevel + "|qual:" + gemQuality + "|corr:" + gemCorrupted
                    + "|shaper:" + shaper + "|elder:" + elder + "|enchantBottomRange:" + enchantMin
                    + "|enchantTopRange:" + enchantMax;

            logger.error("Unknown " + (category == null ? "category" : "group") + " for: " + msg);
        }
    }

    /**
     * Form the unique database key
     */
    public void buildKey() {
        key = new Key(this);
    }

    /**
     * Extracts category strings from the object
     */
    private void findCategory() {
        String apiCategory = apiItem.getCategory().keySet().toArray()[0].toString();
        String apiGroup = null;

        // Get first group if present
        if (apiItem.getCategory().get(apiCategory).size() > 0) {
            apiGroup = apiItem.getCategory().get(apiCategory).get(0).toLowerCase();
        }

        // Extract item's category from its icon
        // If icon path is "http://web.poecdn.com/image/Art/2DItems/Armours/Helmets/HarbingerShards/Shard1.png" then
        // icon category is "HarbingerShards"
        String[] splitItemType = apiItem.getIcon().split("/");
        String iconCategory = splitItemType[splitItemType.length - 2].toLowerCase();

        // Set the item's category based on its properties
        determineCategory(apiCategory, apiGroup, iconCategory);
    }

    /**
     * Set the item's category based on its properties. Warning: messy code
     */
    private void determineCategory(String apiCategory, String apiGroup, String iconCategory) {
        if (apiCategory.equals("monsters") || apiCategory.equals("leaguestones")) {
            discard = true;
            return;
        }

        // Override for enchantments
        if (this.getClass().equals(EnchantBranch.class)) {
            category = CategoryEnum.enchantment;

            // Check all groups and find matching one
            for (GroupEnum groupEnum : category.getGroups()) {
                if (groupEnum.getName().equals(apiGroup)) {
                    group = groupEnum;
                    return;
                }
            }

            discard = true;
            return;
        }

        // Override for item bases
        if (this.getClass().equals(BaseBranch.class)) {
            category = CategoryEnum.base;

            // Check all groups and find matching one
            for (GroupEnum groupEnum : category.getGroups()) {
                if (groupEnum.getName().equals(apiGroup)) {
                    group = groupEnum;
                    return;
                }
            }

            // There was no matching group which means this base is something we don't collect
            discard = true;
            return;
        }


        // Has to be before currency block as technically prophecies are counted as currency
        if (apiItem.getFrameType() == 8) {
            category = CategoryEnum.prophecy;
            group = GroupEnum.prophecy;
            return;
        }

        if (apiCategory.equals("currency")) {
            category = CategoryEnum.currency;

            if (iconCategory.equals("currency")) {
                group = GroupEnum.currency;
            } else if (iconCategory.equals("essence")) {
                group = GroupEnum.essence;
            } else if ("piece".equals(apiGroup)) { // harbinger pieces
                group = GroupEnum.piece;
            } else if (name.startsWith("Vial of ")) { // vials
                group = GroupEnum.vial;
            } else if ("resonator".equals(apiGroup)) {
                group = GroupEnum.resonator;
            } else if ("fossil".equals(apiGroup)) {
                group = GroupEnum.fossil;
            } else if (iconCategory.equals("breach")) { // breach splinters
                group = GroupEnum.splinter;
            } else if (name.startsWith("Timeless") && name.endsWith("Splinter")) { // legion splinters
                group = GroupEnum.splinter;
            } else if ("incubator".equals(apiGroup)) {
                group = GroupEnum.incubator;
            } else if (iconCategory.equals("divination")) { // stacked deck
                group = GroupEnum.currency;
            }

            return;
        }

        if (apiCategory.equals("gems")) {
            category = CategoryEnum.gem;

            if (iconCategory.equals("vaalgems")) {
                group = GroupEnum.vaal;
            } else if ("activegem".equals(apiGroup)) {
                group = GroupEnum.skill;
            } else if ("supportgem".equals(apiGroup)) {
                group = GroupEnum.support;
            }

            return;
        }

        if (apiCategory.equals("maps")) {
            category = CategoryEnum.map;

            if (apiItem.getFrameType() == 3 || apiItem.getFrameType() == 9) {
                group = GroupEnum.unique;
            } else if (iconCategory.equals("breach")) {
                group = GroupEnum.fragment;
            } else if (iconCategory.equals("scarabs")) {
                group = GroupEnum.scarab;
            } else if (apiItem.getProperties() == null) {
                group = GroupEnum.fragment;
            } else {
                group = GroupEnum.map;
            }

            return;
        }

        if (apiCategory.equals("cards")) {
            category = CategoryEnum.card;
            group = GroupEnum.card;
            return;
        }

        if (apiCategory.equals("flasks")) {
            category = CategoryEnum.flask;
            group = GroupEnum.flask;
            return;
        }

        if (apiCategory.equals("jewels")) {
            category = CategoryEnum.jewel;
            group = GroupEnum.jewel;
            return;
        }

        if (apiCategory.equals("armour")) {
            category = CategoryEnum.armour;

            // Check all groups and find matching one
            for (GroupEnum groupEnum : category.getGroups()) {
                if (groupEnum.getName().equals(apiGroup)) {
                    group = groupEnum;
                    return;
                }
            }

            return;
        }

        if (apiCategory.equals("weapons")) {
            category = CategoryEnum.weapon;

            // Check all groups and find matching one
            for (GroupEnum groupEnum : category.getGroups()) {
                if (groupEnum.getName().equals(apiGroup)) {
                    group = groupEnum;
                    return;
                }
            }

            return;
        }

        if (apiCategory.equals("accessories")) {
            category = CategoryEnum.accessory;

            // Check all groups and find matching one
            for (GroupEnum groupEnum : category.getGroups()) {
                if (groupEnum.getName().equals(apiGroup)) {
                    group = groupEnum;
                    return;
                }
            }
        }
    }


    //------------------------------------------------------------------------------------------------------------
    // Getters
    //------------------------------------------------------------------------------------------------------------

    public static void setRelationManager(RelationManager relationManager) {
        Item.relationManager = relationManager;
    }

    public boolean isDiscard() {
        return discard;
    }

    public CategoryEnum getCategory() {
        return category;
    }

    public GroupEnum getGroup() {
        return group;
    }

    public List<String> getExplicitMods() {
        return apiItem.getExplicitMods();
    }

    public Integer getStackSize() {
        return stackSize;
    }

    public Key getKey() {
        return key;
    }

    public Integer getMaxStackSize() {
        return maxStackSize;
    }
}
