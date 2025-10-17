package com.base;

class Const {
    static final String SERVICE_ACTION = "com.base.action.Connect";
    static final String PACKAGE = "com.base.launcher";
    static final String LEGACY_PACKAGE = "ru.headwind.kiosk";
    static final String ADMIN_RECEIVER_CLASS = "com.base.launcher.AdminReceiver";

    public static final String INTENT_PUSH_NOTIFICATION_PREFIX = "com.base.push.";
    public static final String INTENT_PUSH_NOTIFICATION_EXTRA = "com.base.PUSH_DATA";

    public static final String LOG_TAG ="HeadwindMDMAPI";

    public static final String NOTIFICATION_CONFIG_UPDATED = "com.base.push.configUpdated";

    public static final int HMDM_RECONNECT_DELAY_FIRST = 5000;
    public static final int HMDM_RECONNECT_DELAY_NEXT = 60000;
}
