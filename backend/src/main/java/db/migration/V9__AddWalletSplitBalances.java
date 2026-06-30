package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

public class V9__AddWalletSplitBalances extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();
        try (Statement stmt = connection.createStatement()) {
            if (!columnExists(metaData, "wallets", "deposit_balance_paise")) {
                stmt.execute("ALTER TABLE wallets ADD COLUMN deposit_balance_paise BIGINT NOT NULL DEFAULT 0");
            }
            if (!columnExists(metaData, "wallets", "winning_balance_paise")) {
                stmt.execute("ALTER TABLE wallets ADD COLUMN winning_balance_paise BIGINT NOT NULL DEFAULT 0");
            }
            stmt.execute("UPDATE wallets SET deposit_balance_paise = balance_paise WHERE deposit_balance_paise = 0 AND winning_balance_paise = 0");

            if (!columnExists(metaData, "transactions", "deposit_paise")) {
                stmt.execute("ALTER TABLE transactions ADD COLUMN deposit_paise BIGINT NOT NULL DEFAULT 0");
            }
            if (!columnExists(metaData, "transactions", "winning_paise")) {
                stmt.execute("ALTER TABLE transactions ADD COLUMN winning_paise BIGINT NOT NULL DEFAULT 0");
            }
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String table, String column) throws Exception {
        try (ResultSet rs = metaData.getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = metaData.getColumns(null, null, table.toUpperCase(), column.toUpperCase())) {
            if (rs.next()) return true;
        }
        return false;
    }
}
