package poe.manager.entry;

import java.text.DecimalFormat;

/**
 * The default format that new entries are stored as before uploading to database
 */
public class RawEntry {
    private String accountName;
    private double price;
    private int id_l, id_d;
    private long id;

    private static DecimalFormat decimalFormat;

    //------------------------------------------------------------------------------------------------------------
    // Equality methods to root out duplicates in a Set
    //------------------------------------------------------------------------------------------------------------

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (!RawEntry.class.isAssignableFrom(obj.getClass())) {
            return false;
        }

        final RawEntry other = (RawEntry) obj;

        if (this.accountName == null ? (other.accountName != null) : !this.accountName.equals(other.accountName)) {
            return false;
        }

        if (this.id_l != other.id_l) {
            return false;
        }

        return this.id_d == other.id_d;
    }

    @Override
    public int hashCode() {
        int hash = 3;

        hash = 53 * hash + (this.accountName != null ? this.accountName.hashCode() : 0);
        hash = 53 * hash + this.id_l;
        hash = 53 * hash + this.id_d;

        return hash;
    }

    //------------------------------------------------------------------------------------------------------------
    // Getters and Setters
    //------------------------------------------------------------------------------------------------------------


    public String getPrice() {
        return decimalFormat.format(price);
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setLeagueId(int id) {
        this.id_l = id;
    }

    public void setItemId(int id) {
        this.id_d = id;
    }

    public int getLeagueId() {
        return id_l;
    }

    public int getItemId() {
        return id_d;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public static void setDecimalFormat(String pattern) {
        decimalFormat = new DecimalFormat(pattern);
    }
}
