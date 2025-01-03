package com.chua.starter.pay.support.sign;

import com.chua.common.support.lang.date.DateUtils;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;

/**
 * @author Administrator
 */
public class KeyGenerator {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String INTEGERS = "012345678";
    private static final int KEY_LENGTH = 32;


    public static String generateRandomKey() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(KEY_LENGTH);

        for (int i = 0; i < KEY_LENGTH; i++) {
            int randomIndex = random.nextInt(CHARACTERS.length());
            char randomChar = CHARACTERS.charAt(randomIndex);
            sb.append(randomChar);
        }

        return sb.toString();
    }

    public static String generateRandomKeyInt() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);

        Date nowDate = new Date();
        String yyyyMMdd = DateUtils.format(new Date(), "yyyyMMdd");
        sb.append(yyyyMMdd);
        Random random1 = new Random();
        int rdm =random1.nextInt(10000);
        sb.append(rdm);
        return sb.toString();
    }

    public static void main(String[] args) {
        String s = KeyGenerator.generateRandomKeyInt();
        System.out.println(s);
    }
}
