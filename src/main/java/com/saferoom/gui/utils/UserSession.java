package com.saferoom.gui.utils;

import com.saferoom.oauth.UserInfo;

/**
 * Singleton class to manage current user session
 */
public class UserSession {
    private static UserSession instance;
    private UserInfo currentUser;
    private String authMethod; // "oauth", "traditional", etc.
    
    private UserSession() {}
    
    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }
    
    public void setCurrentUser(UserInfo user, String method) {
        this.currentUser = user;
        this.authMethod = method;
    }
    
    public UserInfo getCurrentUser() {
        return currentUser;
    }
    
    public String getAuthMethod() {
        return authMethod;
    }
    
    public String getDisplayName() {
        if (currentUser != null) {
            // Önce name'i, yoksa email'den username'i çıkar
            if (currentUser.getName() != null && !currentUser.getName().isEmpty()) {
                return currentUser.getName();
            } else if (currentUser.getEmail() != null) {
                return currentUser.getEmail().split("@")[0]; // Email'den username
            }
        }
        return "User";
    }
    
    public String getUserInitials() {
        String displayName = getDisplayName();
        if (displayName.length() > 0) {
            return displayName.substring(0, 1).toUpperCase();
        }
        return "U";
    }
    
    public void clearSession() {
        currentUser = null;
        authMethod = null;
    }
}
