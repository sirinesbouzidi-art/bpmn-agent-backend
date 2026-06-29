package com.example.bpmn.dto.admin;

import java.util.List;

public class AdminDashboardResponse {

    private long totalUsers;
    private long totalAdmins;
    private long totalStandardUsers;
    private long generatedModels;
    private long generatedToday;
    private List<Integer> generationPerDay;
    private List<DashboardProcessDTO> latestProcesses;
    private List<DashboardActivityDTO> recentActivities;
    private DashboardSystemStatusDTO systemStatus;

    public AdminDashboardResponse(long totalUsers, long totalAdmins, long totalStandardUsers, long generatedModels,
                                  long generatedToday, List<Integer> generationPerDay,
                                  List<DashboardProcessDTO> latestProcesses,
                                  List<DashboardActivityDTO> recentActivities,
                                  DashboardSystemStatusDTO systemStatus) {
        this.totalUsers = totalUsers;
        this.totalAdmins = totalAdmins;
        this.totalStandardUsers = totalStandardUsers;
        this.generatedModels = generatedModels;
        this.generatedToday = generatedToday;
        this.generationPerDay = generationPerDay;
        this.latestProcesses = latestProcesses;
        this.recentActivities = recentActivities;
        this.systemStatus = systemStatus;
    }

    public long getTotalUsers() { return totalUsers; }
    public long getTotalAdmins() { return totalAdmins; }
    public long getTotalStandardUsers() { return totalStandardUsers; }
    public long getGeneratedModels() { return generatedModels; }
    public long getGeneratedToday() { return generatedToday; }
    public List<Integer> getGenerationPerDay() { return generationPerDay; }
    public List<DashboardProcessDTO> getLatestProcesses() { return latestProcesses; }
    public List<DashboardActivityDTO> getRecentActivities() { return recentActivities; }
    public DashboardSystemStatusDTO getSystemStatus() { return systemStatus; }
}