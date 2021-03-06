package com.comsince.github.security;

import io.netty.util.internal.StringUtil;

import java.util.Base64;

public class Tokenor {
	private static String KEY = "testim";

	public static void setKey(String key) {
	    if (!StringUtil.isNullOrEmpty(key)) {
            KEY = key;
        }
    }

    public static String getUserId(byte[] password) {
        try {
            String signKey =
                DES.decryptDES(Base64.getEncoder().encodeToString(password));

            if (signKey.startsWith(KEY + "|")) {
                signKey = signKey.substring(KEY.length() + 1);
                long timestamp = Long.parseLong(signKey.substring(0, signKey.indexOf('|')));
                if (System.currentTimeMillis() - timestamp > 7 * 24 * 60 *60 *1000) {
                    //return false;
                }
                String id = signKey.substring(signKey.indexOf('|') + 1);
                return id;
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
        }
        return null;
    }
    public static String getToken(String username) {
        String signKey = KEY + "|" + (System.currentTimeMillis()) + "|" + username;
        try {
            return DES.encryptDES(signKey);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
}
