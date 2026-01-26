package com.brgroup.cybotstar.examples.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * SessionExample 的 Mock 数据
 * 用于演示 AgentClient 的多轮对话功能
 */
public class SessionMockData {

    /**
         * 表格行数据
         */
        public record TableRow(String bankName, String currency, long balance) {
    }

    /**
     * 表格列头
     */
    public static final String[] HEADERS = { "Bank Name", "Currency", "Balance" };

    /**
     * 表格数据
     */
    public static final List<TableRow> TABLE_DATA = new ArrayList<>();

    static {
        TABLE_DATA.add(new TableRow("China Merchants Bank", "CNY", 1500000));
        TABLE_DATA.add(new TableRow("HSBC", "HKD", 850000));
        TABLE_DATA.add(new TableRow("Citibank", "USD", 320000));
    }

    /**
     * 构建表格数据的字符串表示
     *
     * @return 格式化的表格数据字符串
     */
    public static String buildTableDataString() {
        StringBuilder dataContext = new StringBuilder();
        dataContext.append("数据表格：\n");
        dataContext.append(String.join(" | ", HEADERS)).append("\n\n");

        for (TableRow row : TABLE_DATA) {
            dataContext.append(row.bankName).append(" | ")
                    .append(row.currency).append(" | ")
                    .append(row.balance).append("\n");
        }

        return dataContext.toString();
    }
}

