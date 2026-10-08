package com.freepark.local.dbconfig;

public final class DatabaseJdbc {

    public static final String JDBC_PARAMS =
            "createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                    + "&connectionTimeZone=UTC&rewriteBatchedStatements=true";

    private DatabaseJdbc() {
    }

    public static String jdbcUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + JDBC_PARAMS;
    }

    public static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String value = host.trim();
        if (value.startsWith("jdbc:mysql://")) {
            value = value.substring("jdbc:mysql://".length());
        }
        int slash = value.indexOf('/');
        if (slash >= 0) {
            value = value.substring(0, slash);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(0, colon);
        }
        return value;
    }
}
