package com.autotune.http.results;

import com.autotune.database.helper.DBConstants;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * The same daily partition statements {@code ExperimentDAOImpl.createPartitions} runs
 * when Postgres reports a missing partition. No new partition strategy.
 */
public final class PartitionSql {
    private PartitionSql() {
    }

    public static List<String> statements(LocalDateTime end) {
        List<String> sql = new ArrayList<>();
        int dayOfTheMonth = end.getDayOfMonth();
        LocalDateTime back = end.minus(DBConstants.PARTITION_TYPES.LAST_N_DAYS, ChronoUnit.DAYS);
        if (back.getMonth() != end.getMonth()) {
            back = end.minusDays(DBConstants.PARTITION_TYPES.LAST_N_DAYS);
        }
        month(sql, back.getYear(), back.getMonthValue(), back.getDayOfMonth());
        if (dayOfTheMonth < DBConstants.PARTITION_TYPES.PARTITION_DAY) {
            month(sql, end.getYear(), end.getMonthValue(), 1);
        } else {
            month(sql, end.getYear(), end.getMonthValue(), dayOfTheMonth);
            YearMonth next = nextMonth(YearMonth.of(end.getYear(), end.getMonthValue()));
            month(sql, next.getYear(), next.getMonthValue(), 1);
        }
        return sql;
    }

    private static void month(List<String> sql, int year, int month, int fromDay) {
        YearMonth yearMonth = YearMonth.of(year, month);
        String monthText = String.format("%02d", month);
        String yearText = String.valueOf(year);
        int last = yearMonth.lengthOfMonth();
        for (int day = fromDay; day <= last; day++) {
            String dayText = String.format("%02d", day);
            sql.add(statement(DBConstants.TABLE_NAMES.KRUIZE_RESULTS, yearText, monthText, dayText));
            sql.add(statement(DBConstants.TABLE_NAMES.KRUIZE_RECOMMENDATIONS, yearText, monthText, dayText));
        }
    }

    private static String statement(String table, String year, String month, String day) {
        return String.format(DBConstants.SQLQUERY.DB_PARTITION_DATERANGE,
                table, year, month, day, table, year, month, day, year, month, day);
    }

    private static YearMonth nextMonth(YearMonth yearMonth) {
        int year = yearMonth.getYear();
        int month = yearMonth.getMonthValue() + 1;
        if (month > 12) {
            month = 1;
            year += 1;
        }
        return YearMonth.of(year, month);
    }
}
