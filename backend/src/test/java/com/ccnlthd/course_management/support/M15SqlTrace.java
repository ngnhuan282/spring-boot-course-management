package com.ccnlthd.course_management.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/** Counts SELECT statements touching each table; a JOIN may touch both tables. */
public class M15SqlTrace implements StatementInspector {
    private static final Pattern SELECT = Pattern.compile("(?is)^\\s*select\\b");
    private static final Pattern COURSE = Pattern.compile("(?i)\\b(?:from|join)\\s+(?:\\w+\\.)?courses\\b");
    private static final Pattern CATEGORY = Pattern.compile("(?i)\\b(?:from|join)\\s+(?:\\w+\\.)?categories\\b");
    private final List<String> statements = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        statements.add(sql);
        return sql;
    }

    public void clear() {
        statements.clear();
    }

    public Snapshot snapshot() {
        List<String> copy = List.copyOf(statements);
        List<String> selects = copy.stream().filter(sql -> SELECT.matcher(sql).find()).toList();
        return new Snapshot(selects.size(),
                selects.stream().filter(sql -> COURSE.matcher(sql).find()).count(),
                selects.stream().filter(sql -> CATEGORY.matcher(sql).find()).count(), copy);
    }

    public record Snapshot(long selectStatements, long courseSelects, long categorySelects,
                           List<String> statements) {
    }
}
