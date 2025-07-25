package com.tungsten.fcl.upgrade;

import android.content.Context;

import com.tungsten.fcl.R;
import com.tungsten.fcllibrary.util.LocaleUtils;

import java.util.ArrayList;

public class RemoteVersion {

    private final String type;
    private final int versionCode;
    private final String versionName;
    private final String date;
    private final ArrayList<Description> description;
    private final String url;
    private final String netdiskUrl;
    private final String netdiskPasswd;

    public RemoteVersion(String type, int versionCode, String versionName, String date, ArrayList<Description> description, String url, String netdiskUrl, String netdiskPasswd) {
        this.type = type;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.date = date;
        this.description = description;
        this.url = url;
        this.netdiskUrl = netdiskUrl;
        this.netdiskPasswd = netdiskPasswd;
    }

    public String getType() {
        return type;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getDate() {
        return date;
    }

    public ArrayList<Description> getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public String getNetdiskUrl() {
        return netdiskUrl;
    }

    public String getNetdiskPasswd() {
        return netdiskPasswd;
    }

    public boolean isBeta() {
        return !getType().equals("release");
    }

    public String getDisplayDescription(Context context) {
        if (description.size() == 0) {
            return "";
        }
        for (Description d : description) {
            if (d.getLang() == null || LocaleUtils.getLocale(LocaleUtils.getLanguage(context)).toString().contains(d.getLang())) {
                return d.getText();
            }
        }
        return description.get(0).getText();
    }

    public static class Description {

        private final String lang;
        private final String text;

        public Description(String lang, String text) {
            this.lang = lang;
            this.text = text;
        }

        public String getLang() {
            return lang;
        }

        public String getText() {
            return text;
        }

    }

}
