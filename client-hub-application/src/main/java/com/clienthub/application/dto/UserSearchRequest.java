package com.clienthub.application.dto;

public class UserSearchRequest {
    private String keyword;
    private String role;
    private Boolean active;
    private int page = 0;
    private int pageSize = 20;
    private String sortBy = "createdAt";
    private String sortDir = "desc";

    public UserSearchRequest(String keyword, String role, Boolean active, int page, int pageSize, String sortBy, String sortDir) {
        this.keyword = keyword;
        this.role = role;
        this.active = active;
        this.page = page;
        this.pageSize = pageSize;
        this.sortBy = sortBy;
        this.sortDir = sortDir;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }
}
