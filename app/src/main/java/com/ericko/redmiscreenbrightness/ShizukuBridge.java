package com.ericko.redmiscreenbrightness;

import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

public final class ShizukuBridge {
    public static final int REQUEST_CODE = 7020;

    private static volatile String lastError = "";

    private ShizukuBridge() {
    }

    public static boolean isAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable error) {
            lastError = messageOf(error);
            return false;
        }
    }

    public static boolean hasPermission() {
        if (!isAvailable()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable error) {
            lastError = messageOf(error);
            return false;
        }
    }

    public static boolean requestPermission() {
        if (!isAvailable()) {
            lastError = "Shizuku is not running";
            return false;
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            Shizuku.requestPermission(REQUEST_CODE);
            return true;
        } catch (Throwable error) {
            lastError = messageOf(error);
            return false;
        }
    }

    public static boolean runShellCommand(String command) {
        if (!isAvailable()) {
            lastError = "Shizuku is not running";
            return false;
        }
        if (!hasPermission()) {
            lastError = "Shizuku permission is not granted";
            return false;
        }

        Object process = null;
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess",
                    String[].class,
                    String[].class,
                    String.class
            );
            newProcess.setAccessible(true);
            process = newProcess.invoke(
                    null,
                    new Object[]{new String[]{"sh", "-c", command}, null, null}
            );

            Method waitFor = process.getClass().getMethod("waitFor");
            waitFor.setAccessible(true);
            int exitCode = (Integer) waitFor.invoke(process);
            if (exitCode != 0) {
                lastError = "Shizuku shell exited with code " + exitCode;
                return false;
            }

            lastError = "";
            return true;
        } catch (Throwable error) {
            lastError = messageOf(error);
            return false;
        } finally {
            if (process != null) {
                try {
                    Method destroy = process.getClass().getMethod("destroy");
                    destroy.setAccessible(true);
                    destroy.invoke(process);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static String getLastError() {
        return lastError == null ? "" : lastError;
    }

    private static String messageOf(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = cause.getClass().getSimpleName();
        }
        return message;
    }
}
