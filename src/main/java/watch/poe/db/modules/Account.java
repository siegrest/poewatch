package poe.db.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import poe.db.Database;
import poe.manager.account.AccountRelation;
import poe.manager.entry.AccountEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Account {
    private static Logger logger = LoggerFactory.getLogger(Account.class);
    private Database database;

    public Account(Database database) {
        this.database = database;
    }

    /**
     * Uploads gathered account and character names to the account database
     *
     * @param accountSet Set of AccountEntries, containing accountName and characterName
     * @return True on success
     */
    public boolean uploadAccountNames(Set<AccountEntry> accountSet) {
        String query1 = "INSERT INTO account_accounts   (name) VALUES (?) ON DUPLICATE KEY UPDATE seen = NOW(); ";
        String query2 = "INSERT INTO account_characters (name) VALUES (?) ON DUPLICATE KEY UPDATE seen = NOW(); ";

        String query3 = "INSERT INTO account_relations (id_l, id_a, id_c) " +
                        "SELECT ?, " +
                        "  (SELECT id FROM account_accounts   WHERE name = ? LIMIT 1), " +
                        "  (SELECT id FROM account_characters WHERE name = ? LIMIT 1) " +
                        "ON DUPLICATE KEY UPDATE seen = NOW(); ";

        try {
            if (database.connection.isClosed()) {
                return false;
            }

            int counter;

            try (PreparedStatement statement = database.connection.prepareStatement(query1)) {
                counter = 0;

                for (AccountEntry accountEntry : accountSet) {
                    statement.setString(1, accountEntry.account);
                    statement.addBatch();

                    if (++counter % 500 == 0) statement.executeBatch();
                }

                statement.executeBatch();
            }


            try (PreparedStatement statement = database.connection.prepareStatement(query2)) {
                counter = 0;

                for (AccountEntry accountEntry : accountSet) {
                    statement.setString(1, accountEntry.character);
                    statement.addBatch();

                    if (++counter % 500 == 0) statement.executeBatch();
                }

                statement.executeBatch();
            }

            try (PreparedStatement statement = database.connection.prepareStatement(query3)) {
                counter = 0;

                for (AccountEntry accountEntry : accountSet) {
                    statement.setInt(1, accountEntry.league);
                    statement.setString(2, accountEntry.account);
                    statement.setString(3, accountEntry.character);
                    statement.addBatch();

                    if (++counter % 500 == 0) statement.executeBatch();
                }

                statement.executeBatch();
            }

            database.connection.commit();
            return true;
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Gets potential accounts that might have changed names
     *
     * @param accountRelations Empty List of AccountRelation to be filled
     * @return True on success
     */
    public boolean getAccountRelations(List<AccountRelation> accountRelations) {
        // Very nice
        String query =  "SELECT oldAcc.id         AS oldAccountId, " +
                        "       oldAcc.accName    AS oldAccountName, " +
                        "       newAcc.id         AS newAccountId, " +
                        "       newAcc.accName    AS newAccountName, " +
                        "       COUNT(oldAcc.idC) AS matches " +
                        "FROM ( " +
                        "    SELECT   a.id   AS id, " +
                        "             a.name AS accName, " +
                        "             c.id   AS idC, " +
                        "             a.seen AS seen " +
                        "    FROM     account_relations  AS r " +
                        "    INNER JOIN ( " +
                        "        SELECT   id_c " +
                        "        FROM     account_relations  " +
                        "        GROUP BY id_c  " +
                        "        HAVING   COUNT(*) > 1 " +
                        "    ) AS tmp1 ON tmp1.id_c = r.id_c " +
                        "    JOIN     account_accounts   AS a ON a.id = r.id_a " +
                        "    JOIN     account_characters AS c ON c.id = r.id_c " +
                        ") AS oldAcc " +
                        "JOIN ( " +
                        "    SELECT   a.id   AS id, " +
                        "             a.name AS accName, " +
                        "             c.id   AS idC, " +
                        "             a.seen AS seen " +
                        "    FROM     account_relations  AS r " +
                        "    INNER JOIN ( " +
                        "        SELECT   id_c " +
                        "        FROM     account_relations  " +
                        "        GROUP BY id_c  " +
                        "        HAVING   COUNT(*) > 1 " +
                        "    ) AS tmp1 ON tmp1.id_c = r.id_c " +
                        "    JOIN     account_accounts   AS a ON a.id = r.id_a " +
                        "    JOIN     account_characters AS c ON c.id = r.id_c " +
                        ") AS newAcc " +
                        "  ON      oldAcc.idC  = newAcc.idC " +
                        "    AND   oldAcc.seen < newAcc.seen " +
                        "    AND   oldAcc.id  != newAcc.id " +
                        "LEFT JOIN account_history AS h " +
                        "  ON      h.id_old    = oldAcc.id " +
                        "    AND   h.id_new    = newAcc.id " +
                        "WHERE     h.id_old IS NULL " +
                        "GROUP BY  oldAcc.id, newAcc.id " +
                        "HAVING    matches > 1 " +
                        "ORDER BY  oldAcc.seen ASC, newAcc.seen ASC; ";

        ArrayList<Long> filter = new ArrayList<>();

        try {
            if (database.connection.isClosed()) {
                return false;
            }

            try (Statement statement = database.connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery(query);

                while (resultSet.next()) {
                    long id = resultSet.getLong("oldAccountId");
                    if (filter.contains(id)) continue;
                    filter.add(id);

                    AccountRelation accountRelation = new AccountRelation();
                    accountRelation.load(resultSet);
                    accountRelations.add(accountRelation);
                }
            }

            return true;
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Creates an entry in table `account_history`, indicating account name change
     *
     * @param accountRelations List of AccountRelation to be created
     * @return True on success
     */
    public boolean createAccountRelation(List<AccountRelation> accountRelations) {
        String query = "INSERT INTO account_history (id_old, id_new, moved) VALUES (?, ?, ?); ";

        try {
            if (database.connection.isClosed()) {
                return false;
            }

            try (PreparedStatement statement = database.connection.prepareStatement(query)) {
                for (AccountRelation accountRelation : accountRelations) {
                    statement.setLong(1, accountRelation.oldAccountId);
                    statement.setLong(2, accountRelation.newAccountId);
                    statement.setInt(3, accountRelation.moved);
                    statement.addBatch();
                }

                statement.executeBatch();
            }

            database.connection.commit();
            return true;
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }
    }
}
