package com.itheima.bolee.insurance.dto.ai.terms;

import java.util.List;

public class InsuranceTermSourcePageVO {
    private long page;
    private int pageSize;
    private long total;
    private List<InsuranceTermSourceVO> records;

    public InsuranceTermSourcePageVO(long page, int pageSize, long total, List<InsuranceTermSourceVO> records) {
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
    }

    public long getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotal() { return total; }
    public List<InsuranceTermSourceVO> getRecords() { return records; }
}
